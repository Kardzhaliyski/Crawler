package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    public static final Pattern LINK_VERIFYING_PATTERN = Pattern.compile("^(/|http[s]?:/).+");
    public static final Pattern IMAGE_NAME_PATTERN = Pattern.compile("(?<=/)([^/?])+(?=$|\\?)");
    private static final String IMAGE_DIRECTORY = "C:\\Users\\Zdravko\\Pictures\\crawler\\";
    public static Set<String> downloadedImages = Collections.synchronizedSet(new HashSet<>());
    public static Set<String> visitedLinks = Collections.synchronizedSet(new HashSet<>());
    public static BlockingQueue<String> tasks = new LinkedBlockingQueue<>();
    public static String baseUri;
    public static AtomicInteger pageCount = new AtomicInteger(0);
    public static AtomicInteger imgCount = new AtomicInteger(0);


    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 1) {
            System.out.println("Invalid input!");
            System.out.println("Usage: crawl [url]]");
            return;
        }
        String inputUrl = args[0];
        if (!LINK_VERIFYING_PATTERN.matcher(inputUrl).matches()) {
            inputUrl = "https://" + inputUrl;
        }

        Document document = Jsoup.connect(inputUrl).get();
        baseUri = document.baseUri();
        visitedLinks.add(baseUri);
        tasks.put(baseUri);

        ExecutorService threadPool = null;
        try {
            threadPool = Executors.newCachedThreadPool();
            while (true) {
                String url = tasks.poll(10, TimeUnit.SECONDS);
                if (url == null) {
                    break;
                }

                Runnable crawlTask = getCrawlTask(url);
                threadPool.submit(crawlTask);
            }
        } finally {
            if (threadPool != null) {
                threadPool.shutdown();
                threadPool.awaitTermination(1, TimeUnit.HOURS);
                System.out.println("tasks queue is empty: " + tasks.isEmpty());
            }
        }


        System.out.println("---------------------------------------");
        System.out.printf("Pages crawled: %d. Downloaded images: %d.", pageCount.get(), imgCount.get());
    }

    public static void crawl(String url) throws IOException, InterruptedException {
        crawl(Jsoup.connect(url).get());
    }

    public static void crawl(Document document) throws InterruptedException, IOException {
        System.out.println("Extracting data from : " + document.baseUri());

        pageCount.incrementAndGet();

        extractLinks(document);
        extractImages(document);
    }

    private static void extractLinks(Document document) throws InterruptedException {
        Elements links = document.select("a[href]");
        for (Element link : links) {
            String href = link.attr("abs:href");
            if (!href.startsWith(baseUri)) {
                continue;
            }

            int i = href.indexOf('#');
            if (i != -1) {
                href = href.substring(0, i);
            }

            if (visitedLinks.contains(href)) {
                continue;
            }

            Matcher matcher = LINK_VERIFYING_PATTERN.matcher(href);
            if (!matcher.find()) {
                continue;
            }

            visitedLinks.add(href);
            tasks.put(href);
        }
    }

    private static void extractImages(Document document) throws IOException {
        Elements images = document.select("img[src]");
        for (Element img : images) {
            String path = img.attr("abs:src");

            Matcher matcher = IMAGE_NAME_PATTERN.matcher(path);
            if (!matcher.find()) {
                continue;
            }

            String name = path.substring(matcher.start(), matcher.end());

            if (downloadedImages.contains(name)) {
                continue;
            }

            File file = new File(IMAGE_DIRECTORY + name);
            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                fileOutputStream.write(Jsoup.connect(path).ignoreContentType(true).execute().bodyAsBytes());
            }

            if (downloadedImages.add(name)) {
                imgCount.incrementAndGet();
            }
        }
    }

    private static Runnable getCrawlTask(String url) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    crawl(url);
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}