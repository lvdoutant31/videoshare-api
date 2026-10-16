package com.yingliu.service.content.video;

import com.yingliu.common.exception.BusinessException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;

import java.util.List;

/** One source of truth for body validation and ordered article covers. */
public final class ContentBody {
    private static final Safelist ALLOWED = new Safelist()
            .addTags("p", "div", "span", "br", "strong", "b", "em", "i", "u", "s", "ul", "ol", "li",
                    "blockquote", "h1", "h2", "h3", "table", "thead", "tbody", "tr", "th", "td", "img", "a")
            .addAttributes(":all", "style")
            .addAttributes("img", "src", "alt", "width", "height")
            .addAttributes("td", "colspan", "rowspan", "data-colwidth")
            .addAttributes("th", "colspan", "rowspan", "data-colwidth")
            .addAttributes("ol", "start")
            .addAttributes("a", "href").addProtocols("a", "href", "http", "https")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer");

    private ContentBody() { }

    public static String normalize(String html, Long mediaId) {
        if (html != null && html.length() > 100000) throw invalid("正文不能超过 100000 个字符");
        var source = Jsoup.parseBodyFragment(html == null ? "" : html);
        source.select("img").stream().filter(image -> !safeImageUrl(image.attr("src"))).forEach(org.jsoup.nodes.Node::remove);
        for (var element : source.select("[style]")) {
            String styles = java.util.Arrays.stream(element.attr("style").split(";"))
                    .map(String::trim).filter(ContentBody::safeStyle)
                    .collect(java.util.stream.Collectors.joining(";"));
            element.attr("style", styles);
        }
        for (var element : source.select("[colspan], [rowspan], [data-colwidth], [width], [height], [start]")) {
            for (String attribute : List.of("colspan", "rowspan", "data-colwidth", "width", "height", "start")) {
                if (element.hasAttr(attribute) && !element.attr(attribute).matches("\\d{1,4}(,\\d{1,4})*")) element.removeAttr(attribute);
            }
        }
        source.outputSettings().prettyPrint(false);
        String clean = Jsoup.clean(source.body().html(), "", ALLOWED,
                new Document.OutputSettings().prettyPrint(false)).trim();
        var body = Jsoup.parseBodyFragment(clean).body();
        boolean hasText = !body.text().replace('\u00a0', ' ').replace("\u200b", "").isBlank();
        if (!hasText && body.select("img[src]").isEmpty()) {
            if (mediaId == null) throw invalid("视频与正文至少填写一项");
            return null;
        }
        return clean;
    }

    public static List<String> images(String html) {
        return Jsoup.parseBodyFragment(html == null ? "" : html).select("img[src]").stream()
                .map(image -> image.attr("src"))
                .filter(ContentBody::safeImageUrl).toList();
    }

    private static boolean safeStyle(String declaration) {
        String[] parts = declaration.split(":", 2);
        if (parts.length != 2) return false;
        String value = parts[1].trim();
        return switch (parts[0].trim().toLowerCase(java.util.Locale.ROOT)) {
            case "text-align" -> value.matches("(?i)(left|center|right|justify)");
            case "color", "background-color" -> value.matches("(?i)(#[0-9a-f]{3,8}|[a-z]+|rgba?\\([\\d\\s.,%]+\\))");
            case "font-family" -> value.matches("(?i)[a-z\\s,\"'-]+");
            case "font-size" -> value.matches("(?i)\\d{1,2}(px|pt)");
            case "line-height" -> value.matches("(?i)(normal|[1-3](\\.\\d+)?)");
            default -> false;
        };
    }

    private static boolean safeImageUrl(String url) {
        return !url.contains("\\") && url.matches("(?i)^(https?://[^\\s]+|/(?!/)[^\\s]+)$");
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
