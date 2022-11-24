package org.example;

import org.apache.commons.cli.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Path;
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
    public static final String DEFAULT_OUTPUT_DIRECTORY = "C:\\Users\\Zdravko\\Pictures\\crawler\\";
    public static Set<String> downloadedImages = Collections.synchronizedSet(new HashSet<>());
    public static Set<String> visitedLinks = Collections.synchronizedSet(new HashSet<>());
    public static BlockingQueue<String> tasks = new LinkedBlockingQueue<>();
    private static String outputDirectory;
    private static NetworkClient client;
    public static String baseUri;
    public static AtomicInteger pageCount = new AtomicInteger(0);
    public static AtomicInteger imgCount = new AtomicInteger(0);

    public static void main(String[] args) throws IOException, InterruptedException {
        CommandLine commandLine = parseCommandLine(args);
        outputDirectory = commandLine.getOptionValue(CrawlerOptions.OUTPUT_DIRECTORY, DEFAULT_OUTPUT_DIRECTORY);
        String userAgent = commandLine.getOptionValue(CrawlerOptions.USER_AGENT, null);
        client = new NetworkClient(userAgent);
        String[] urls = commandLine.getArgs();
        if (urls.length < 1) {
            System.out.println("Invalid input!");
            System.out.println("Usage: crawl [url]]");
            return;
        }

        long end = System.currentTimeMillis();
        long start = System.currentTimeMillis();


        ExecutorService threadPool = null;
        try {
            threadPool = Executors.newCachedThreadPool();
            AtomicInteger activeTasks = new AtomicInteger(0);
            for (String inputUrl : urls) {
                startCrawl(inputUrl);
                while (true) {
                    String url = tasks.poll(2, TimeUnit.SECONDS);
                    if (url == null) {
                        if (activeTasks.get() == 0) {
                            break;
                        }
                    }

                    Runnable crawlTask = getCrawlTask(url, activeTasks);
                    threadPool.submit(crawlTask);
                }
            }

        } finally {
            threadPool.shutdown();
            threadPool.awaitTermination(1, TimeUnit.HOURS);
            end = System.currentTimeMillis();
//                System.out.println("tasks queue is empty: " + tasks.isEmpty());
        }

        System.out.println("---------------------------------------");
        System.out.printf("Pages crawled: %d. Downloaded images: %d.", pageCount.get(), imgCount.get());
        System.out.printf(" Time: %dms.", end - start);
    }

    private static void startCrawl(String inputUrl) throws IOException, InterruptedException {
        if (!LINK_VERIFYING_PATTERN.matcher(inputUrl).matches()) {
            inputUrl = "https://" + inputUrl;
        }

        System.out.println("------------- Starting with : " + inputUrl + " -------------");
        Document document = Jsoup.connect(inputUrl).get();
        baseUri = document.baseUri();
        visitedLinks.add(baseUri);
        tasks.put(baseUri);
    }

    public static void crawl(String url) throws IOException, InterruptedException, URISyntaxException {
//        crawl(Jsoup.connect(url).get());
        String html = client.getHtml(url);
        Document document = Jsoup.parse(html);
        document.setBaseUri(url);
        crawl(document);
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

    private static void extractImages(Document document) {
        Elements images = document.select("img[src]");
        for (Element img : images) {
            String url = img.attr("abs:src");

            Matcher matcher = IMAGE_NAME_PATTERN.matcher(url);
            if (!matcher.find()) {
                continue;
            }

            String name = url.substring(matcher.start(), matcher.end());

            if (downloadedImages.contains(name)) {
                continue;
            }

            Path path = Path.of(outputDirectory + name);

            if (downloadedImages.add(name)) {
                imgCount.incrementAndGet();
            }

            try {
                client.download(url, path);
            } catch (URISyntaxException | InterruptedException | IOException e) {
                System.out.println("Error while downloading image: \"" + name + "\" from link: " + url + " error msg: " + e.getMessage());
            }
        }
    }

    private static Runnable getCrawlTask(String url, AtomicInteger activeTasks) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    activeTasks.incrementAndGet();
                    crawl(url);
                } catch (IOException | InterruptedException | URISyntaxException e) {
                    throw new RuntimeException(e);
                } finally {
                    activeTasks.decrementAndGet();
                }
            }
        };
    }

    private static CommandLine parseCommandLine(String[] args) {
        try {
            return new DefaultParser().parse(CrawlerOptions.getOptions(), args);
        } catch (UnrecognizedOptionException e) {
            System.out.println("grep: Invalid option -- '" + e.getOption() + "'");
//            printUsageInstructions();
            System.exit(-1);
        } catch (ParseException e) {
            System.out.println("grep: Invalid option -- '");
            System.exit(-1);
        }

        return null;
    }
}