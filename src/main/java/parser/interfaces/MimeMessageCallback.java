package parser.interfaces;

import javax.mail.Part;

@FunctionalInterface
public interface MimeMessageCallback {

    void walk(Part part, int level) throws Exception;

}