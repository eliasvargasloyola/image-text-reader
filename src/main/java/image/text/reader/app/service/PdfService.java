package image.text.reader.app.service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
public class PdfService extends PDFStreamEngine {

    public int imageNumber = 1;

    public List<byte[]> getPages2PDF(String nameFile, String str2Find) {
        long start = System.nanoTime();
        PDFParser parser = null;
        PDDocument pdDoc = null;
        COSDocument cosDoc = null;
        File file = new File(nameFile);
        List<CompletableFuture<byte[]>> asyncs = new ArrayList<>();

        try {
            Executor exe = Executors.newFixedThreadPool(25, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });

            parser = new PDFParser(new RandomAccessFile(file, "r"));
            parser.parse();
            cosDoc = parser.getDocument();
            pdDoc = new PDDocument(cosDoc);
            PDPageTree tree = pdDoc.getPages();
            Stream<PDPage> paralelStreamDoc = StreamSupport.stream(tree.spliterator(), true);
            asyncs = paralelStreamDoc.map(o -> CompletableFuture.supplyAsync(() -> {
                PDResources pdResources = o.getResources();
                for (COSName c : pdResources.getXObjectNames()) {
                    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                        PDXObject pdx = pdResources.getXObject(c);
                        if (pdx instanceof PDImageXObject) {
                            imageNumber++;
                            ImageIO.write(((PDImageXObject) pdx).getImage(), "png", outputStream);
                            byte[] response = outputStream.toByteArray();
                            findTextImage(str2Find, response);
                            outputStream.close();
                            System.out.println("============ IMAGES READY [" + imageNumber + "] ==================");
                            return response;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }, exe)).collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
            try {
                if (cosDoc != null)
                    cosDoc.close();
                if (pdDoc != null)
                    pdDoc.close();
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
        long duration = (System.nanoTime() - start) / 1_000_000;
        System.out.println("Done in " + duration + " msecs");
        return asyncs.stream().map(CompletableFuture::join).collect(Collectors.toList());
    }

    public boolean findTextImage(String text2find, byte[] image) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(image)) {
            ITesseract instance = new Tesseract();
            BufferedImage bufferImage = ImageIO.read(input);
            String imgText = instance.doOCR(bufferImage);
            System.out.println(imgText);
            return imgText.toLowerCase().contains(text2find.toLowerCase());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

}
