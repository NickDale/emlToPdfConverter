package parser.entity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;


/**
 *
 *
 * @author nickdale
 * @version 1.0
 */
public class ConvertedFile {

    private File html;
    private File pdf;
    private List<File> attachments;

    public File getPdf() {
        return pdf;
    }

    public void setPdf(File pdf) {
        this.pdf = pdf;
    }

    public File getEmailInHtml() {
        return html;
    }

    public void setEmailInHtml(File emailInHtml) {
        this.html = emailInHtml;
    }

    public List<File> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<File> attachments) {
        this.attachments = attachments;
    }

    public void addAttachment(File attachment) {
        if (isEmpty(this.attachments)) {
            this.attachments = new ArrayList<>();
        }
        this.attachments.add(attachment);
    }

}
