package org.example;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class CrawlerOptions {
    //-output-dir <dir> - директория в която да се записват снимките
    //-image-format <format> - вид снимки които да се записват, например: png или, png,jpeg
    //-user-agent <name> - задава User-Agent, който се подава като header при изпращане на заявка към сървъра
    public static final Option OUTPUT_DIRECTORY = new Option("d", "output-dir" ,true, "Output directory");
    public static final Option IMAGE_FORMAT = new Option("f", "image-format", true, "Image formats to download");
    public static final Option USER_AGENT = new Option("a", "user-agent", true, "User agent");

    public static Options getOptions() {
        Options options = new Options();
        options.addOption(OUTPUT_DIRECTORY)
                .addOption(IMAGE_FORMAT)
                .addOption(USER_AGENT);
        return options;
    }

}
