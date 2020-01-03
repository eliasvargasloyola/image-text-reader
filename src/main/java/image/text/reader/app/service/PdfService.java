package image.text.reader.app.service;

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.model.AmazonRekognitionException;
import com.amazonaws.services.rekognition.model.DetectTextRequest;
import com.amazonaws.services.rekognition.model.DetectTextResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.TextDetection;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
public class PdfService {

    AmazonRekognition rekognitionClient;

    public PdfService(AmazonRekognition rekognitionClient) {
        this.rekognitionClient = rekognitionClient;
    }

    public String getPages2PDF(String nameFile, String str2Find, String directory) {
        PDDocument pdDoc = null;
        COSDocument cosDoc = null;
        File file = new File(nameFile);

        List<CompletableFuture<byte[]>> asyncs = new ArrayList<>();

        try {

            Executor exe = Executors.newFixedThreadPool(3, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });

            PDFParser parser = new PDFParser(new RandomAccessFile(file, "r"));
            parser.parse();
            cosDoc = parser.getDocument();
            pdDoc = new PDDocument(cosDoc);
            PDPageTree tree = pdDoc.getPages();
            AtomicInteger pageNum = new AtomicInteger();

            Stream<PDPage> paralelStreamDoc = StreamSupport.stream(tree.spliterator(), true);
            asyncs = paralelStreamDoc.map(o -> CompletableFuture.supplyAsync(() -> {
                PDResources pdResources = o.getResources();
                Stream<COSName> paralelStreamCos = StreamSupport.stream(pdResources.getXObjectNames().spliterator(), true);
                pageNum.getAndIncrement();
                System.out.println("Page processings ..." + pageNum);
                return paralelStreamCos.map(cosName -> this.findText(cosName, pdResources, str2Find)).filter(Objects::nonNull).findFirst().orElse(null);
            }, exe)).collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
        }
        List<byte[]> images = asyncs.stream().map(CompletableFuture::join).filter(Objects::nonNull).collect(Collectors.toList());
        closeAll(pdDoc, cosDoc);
        return toFile(images, directory);
    }

    public byte[] textImageFromPdf(String nameFile, String str2Find, int numPage) {
        PDDocument pdDoc = null;
        COSDocument cosDoc = null;
        File file = new File(nameFile);
        byte[] response = null;

        try {
            PDFParser parser = new PDFParser(new RandomAccessFile(file, "r"));
            parser.parse();
            cosDoc = parser.getDocument();
            pdDoc = new PDDocument(cosDoc);
            PDPage page = pdDoc.getPage(numPage);
            PDResources pdResources = page.getResources();
            Stream<COSName> paralelStreamCos = StreamSupport.stream(pdResources.getXObjectNames().spliterator(), false);
            return paralelStreamCos.map(cosName -> this.findText(cosName, pdResources, str2Find)).filter(Objects::nonNull).findFirst().orElse(null);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeAll(pdDoc, cosDoc);
        }

        return response;
    }

    private void closeAll(PDDocument pdDoc, COSDocument cosDoc) {
        try {
            if (cosDoc != null)
                cosDoc.close();
            if (pdDoc != null)
                pdDoc.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private byte[] findText(COSName c, PDResources pdResources, String str2Find) {
        byte[] tmp = null;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDXObject pdx = pdResources.getXObject(c);
            if (pdx instanceof PDImageXObject) {
                ImageIO.write(((PDImageXObject) pdx).getImage(), "png", outputStream);
                if (findTextImageAWS(str2Find, outputStream.toByteArray())) {
                    tmp = outputStream.toByteArray();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tmp;
    }

    private boolean findTextImageAWS(String text2find, byte[] inputImage) {

        ByteBuffer imageByte = ByteBuffer.wrap(inputImage);

        List<String> array2Find = Arrays.asList(text2find.split("\n"));
        List<TextDetection> resultDetection = new ArrayList<>();

        DetectTextRequest request = new DetectTextRequest()
                .withImage(new Image().withBytes(imageByte));

        try {
            DetectTextResult result = rekognitionClient.detectText(request);
            List<TextDetection> textDetections = result.getTextDetections();
            resultDetection = textDetections.stream()
                    .filter(detection ->
                            array2Find.stream()
                                    .anyMatch(s -> StringUtils.containsIgnoreCase(detection.getDetectedText(), s)))
                    .collect(Collectors.toList());
        } catch (AmazonRekognitionException e) {
            e.printStackTrace();
        }

        return !resultDetection.isEmpty();
    }

    private String toFile(List<byte[]> images, String directory) {
        String fullPath = directory.concat("/result_" + new Date().getTime() + ".pdf");
        PDDocument doc = new PDDocument();
        try {
            images.forEach(imageBytes -> {
                PDPageContentStream contents = null;
                PDPage page = new PDPage();
                doc.addPage(page);
                try {
                    PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, imageBytes, "Image" + new Date().getTime());
                    contents = new PDPageContentStream(doc, page);
                    contents.drawImage(pdImage, -10, 50, (float) (pdImage.getWidth() / 4.5), (float) (pdImage.getHeight() / 4.4));
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (contents != null)
                            contents.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            doc.save(fullPath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        closeAll(doc, null);
        return fullPath;
    }

}
