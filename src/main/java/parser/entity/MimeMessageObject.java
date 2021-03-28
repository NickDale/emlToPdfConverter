package parser.entity;

import com.sun.mail.util.BASE64DecoderStream;
import org.apache.commons.io.IOUtils;
import parser.interfaces.MimeMessageCallback;
import parser.interfaces.Replacer;

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.ContentType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static javax.print.DocFlavor.CHAR_ARRAY.TEXT_PLAIN;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.tika.mime.MediaType.TEXT_HTML;
import static parser.util.ParserUtil.CHARSET;
import static parser.util.ParserUtil.CONTENT_ID;
import static parser.util.ParserUtil.HTML_META_CHARSET_REGEX;
import static parser.util.ParserUtil.HTML_WRAPPER_TEMPLATE;
import static parser.util.ParserUtil.IMAGE_TYPE;
import static parser.util.ParserUtil.IMG_CID_PLAIN_REGEX;
import static parser.util.ParserUtil.IMG_CID_REGEX;
import static parser.util.ParserUtil.MULTIPART_TYPE;
import static parser.util.ParserUtil.replace;

public class MimeMessageObject {

    private String entry;
    private ContentType contentType;
    private String htmlBody;

    public MimeMessageObject(ContentType contentType, Part part) throws Exception {
        this.contentType = contentType;
        setData(part);
        this.htmlBody = createHtmlBody(part);
    }

    public MimeMessageObject(String entry, ContentType contentType) {
        this.entry = entry;
        this.contentType = contentType;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public void setHtmlBody(String htmlBody) {
        this.htmlBody = htmlBody;
    }

    public void setData(Part part) throws Exception {
        walkMimeStructure(part, 0, (p, level) -> {
            // only process text/plain and text/html
            if (!p.isMimeType(TEXT_PLAIN.toString()) && !p.isMimeType(TEXT_HTML.toString())) {
                return;
            }
            String stringContent = getStringContent(p);
            if (isBlank(stringContent) || Part.ATTACHMENT.equalsIgnoreCase(p.getDisposition())) {
                return;
            }
            // use text/plain entries only when we found nothing before
            if (isBlank(this.entry) || p.isMimeType(TEXT_HTML.toString())) {
                this.entry = stringContent;
                this.contentType = new ContentType(p.getContentType());
            }
        });
    }

    private void walkMimeStructure(Part p, int level, MimeMessageCallback callback) throws Exception {
        callback.walk(p, level);
        if (p.isMimeType(MULTIPART_TYPE)) {
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                walkMimeStructure(mp.getBodyPart(i), level + 1, callback);
            }
        }
    }

    private String getStringContent(Part part) throws IOException, MessagingException {
        Object content;
        try {
            content = part.getContent();
        } catch (Exception e) {
            // most likely the specified charset could not be found
            content = part.getInputStream();
        }
        if (content instanceof String) {
            return (String) content;
        }
        if (content instanceof InputStream) {
            return new String(IOUtils.toByteArray((InputStream) content), UTF_8);
        }
        return null;
    }

    public Charset getCharset() {
        return Charset.forName(this.contentType.getParameter(CHARSET));
    }

    private String createHtmlBody(Part part) throws Exception {
        String htmlBody = ofNullable(this.entry).orElse("");
        Map<String, MimeMessageObject> inlinedImages = getInlinedImage(part);
        String charset = getCharset().name();
        if (this.contentType.match(TEXT_HTML.toString())) {
            if (inlinedImages.size() > 0) {
                // find embedded images and embed them into the html
                htmlBody = replace(htmlBody, IMG_CID_REGEX, appendImage(inlinedImages));
            }
            // overwrite html declared charset with email header charset
            htmlBody = replace(htmlBody, HTML_META_CHARSET_REGEX, matcher -> matcher.group(1) + charset);
        } else {
            htmlBody = "<div style=\"white-space: pre-wrap\">" + htmlBody.replace("\n", "<br>").replace("\r", "") + "</div>";
            htmlBody = String.format(HTML_WRAPPER_TEMPLATE, charset, htmlBody);
            if (inlinedImages.size() > 0) {
                // find embedded images and embed them into the html
                htmlBody = replace(htmlBody, IMG_CID_PLAIN_REGEX, appendImage(inlinedImages, Boolean.TRUE));
            }
        }
        return htmlBody;
    }

    private Map<String, MimeMessageObject> getInlinedImage(Part part) throws Exception {
        Map<String, MimeMessageObject> result = new HashMap<>();
        walkMimeStructure(part, 0, (p, level) -> {
            String[] header = p.getHeader(CONTENT_ID);
            if (p.isMimeType(IMAGE_TYPE) && nonNull(header)) {
                String imageBase64 = Base64.getEncoder().encodeToString(IOUtils.toByteArray((BASE64DecoderStream) p.getContent()));
                result.put(header[0], new MimeMessageObject(imageBase64, new ContentType(p.getContentType())));
            }
        });
        return result;
    }

    private Replacer appendImage(Map<String, MimeMessageObject> inlinedImages) {
        return appendImage(inlinedImages, Boolean.FALSE);
    }

    private Replacer appendImage(Map<String, MimeMessageObject> inlinedImages, boolean withSrcTag) {
        return matcher -> {
            MimeMessageObject base64Entry = inlinedImages.get("<" + matcher.group(1) + ">");
            if (isNull(base64Entry)) {
                return matcher.group();
            }
            String data = "data:" + base64Entry.contentType.getBaseType() + ";base64," + base64Entry.entry + "\"";
            return withSrcTag ? "<img src=\"" + data + " />" : data;
        };
    }

    public void appendToHtmlBody(String str) {
        htmlBody += str;
    }
}
