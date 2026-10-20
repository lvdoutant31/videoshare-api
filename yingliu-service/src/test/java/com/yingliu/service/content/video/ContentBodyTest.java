package com.yingliu.service.content.video;

import com.yingliu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ContentBodyTest {
    @Test void requiresVideoOrMeaningfulBody() {
        for (String value : new String[]{"", " ", "<p><br></p>", "<p>&nbsp;\u200b</p>", "<img src='javascript:alert(1)'>"}) {
            assertThatThrownBy(() -> ContentBody.normalize(value, null)).isInstanceOf(BusinessException.class);
        }
        assertThat(ContentBody.normalize("<p><br></p>", 1L)).isNull();
        assertThat(ContentBody.normalize("<p>文字</p>", null)).contains("文字");
        assertThat(ContentBody.normalize("<img src='https://example.com/a.png'>", null)).contains("a.png");
        assertThat(ContentBody.normalize("<img src='/media/images/a.png'>", null)).contains("/media/images/a.png");
        assertThatThrownBy(() -> ContentBody.normalize("<img src='//evil.example/image.png'>", null)).isInstanceOf(BusinessException.class);
    }
    @Test void cleansUnsafeMarkupAndKeepsLayoutAndImageOrder() {
        String clean = ContentBody.normalize("<p style='text-align:center;background:url(javascript:x)' onclick='alert(1)'>"
                + "<strong>正文</strong></p><script>alert(1)</script><img src='https://example.com/b.png' onerror='alert(1)'>"
                + "<img src='https://example.com/a.png'>", null);
        assertThat(clean).contains("text-align:center", "<strong>正文</strong>")
                .doesNotContain("onclick", "onerror", "script", "background");
        assertThat(ContentBody.images(clean)).containsExactly("https://example.com/b.png", "https://example.com/a.png");
    }
    @Test void preservesEditorStylesAndTableSpansWithoutUnsafeCss() {
        String clean = ContentBody.normalize("<p style='font-family:Arial;font-size:24px;color:rgb(255, 0, 0);background-color:#fff;position:fixed;background-image:url(x)'>Styled</p>"
                + "<table><tbody><tr><td colspan='2' rowspan='1' data-colwidth='120,120'>Cell</td></tr></tbody></table>", null);
        assertThat(clean).contains("font-family:Arial", "font-size:24px", "color:rgb(255, 0, 0)", "colspan=\"2\"", "data-colwidth=\"120,120\"")
                .doesNotContain("position", "url(", "background-image");
        assertThat(ContentBody.normalize(clean, null)).isEqualTo(clean);
    }
}
