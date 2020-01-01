/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package image.text.reader.app;

import image.text.reader.app.service.PdfService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ApplicationTest {

    @Autowired
    PdfService pdfService;

    @Test
    public void testPDF2() {
        ///tmp/scraper/71088 GG.pdf
        long start = System.nanoTime();
        List<byte[]> images = pdfService.getPages2PDF("/tmp/scrapers/71088 GG.pdf", "BILL OF LADING");
        System.out.println("============ IMAGES LOAD [" + images.size() + "] ==================");
        long duration = (System.nanoTime() - start) / 1_000_000;
        System.out.println("Done in " + duration + " msecs");
    }
}