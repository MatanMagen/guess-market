package guessmarket.server;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;

/**
 * {@code POST /upload}, multipart with one part named {@code file} — adds the events of an XML file
 * to the system, with the caller as their market maker.
 * <p>
 * The file is read from the request and handed straight to the engine; it is never written to the
 * server's disk. The size threshold equals the size limit for that reason: Tomcat only spills a
 * part to a temporary file once it passes the threshold, and anything that large is refused first.
 */
@WebServlet("/upload")
@MultipartConfig(fileSizeThreshold = UploadServlet.MAX_FILE_BYTES,
        maxFileSize = UploadServlet.MAX_FILE_BYTES,
        maxRequestSize = UploadServlet.MAX_FILE_BYTES * 2L)
public class UploadServlet extends ApiServlet {

    static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, user -> {
            Part file;
            try {
                file = request.getPart("file");
            } catch (IllegalStateException tooLarge) {
                throw new BadRequestException("The file is larger than " + MAX_FILE_BYTES / (1024 * 1024)
                        + " MB, which is more than any Guess Market file needs.");
            } catch (ServletException notMultipart) {
                throw new BadRequestException("No file was sent.");
            }
            if (file == null) {
                throw new BadRequestException("No file was sent.");
            }
            try (InputStream content = file.getInputStream()) {
                return engine().uploadEvents(content, file.getSubmittedFileName(), user);
            }
        });
    }
}
