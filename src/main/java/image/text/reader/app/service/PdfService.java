package image.text.reader.app.service;

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.model.AmazonRekognitionException;
import com.amazonaws.services.rekognition.model.DetectTextRequest;
import com.amazonaws.services.rekognition.model.DetectTextResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.TextDetection;
import image.text.reader.app.domain.ResultTextAws;
import image.text.reader.app.domain.TextExtractAws;
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
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
public class PdfService {

    AmazonRekognition rekognitionClient;

    public PdfService(AmazonRekognition rekognitionClient) {
        this.rekognitionClient = rekognitionClient;
    }

    public ResultTextAws getPages2PDF(String nameFile, String str2Find, String directory) {
        PDDocument pdDoc = null;
        COSDocument cosDoc = null;
        File file = new File(nameFile);

        List<CompletableFuture<TextExtractAws>> asyncs = new ArrayList<>();

        try {

            Executor exe = Executors.newFixedThreadPool(15, r -> {
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
                Stream<COSName> paralelStreamCos = StreamSupport.stream(pdResources.getXObjectNames().spliterator(), false);
                pageNum.getAndIncrement();
                System.out.println("Page processings ..." + pageNum);
                return paralelStreamCos.map(cosName -> this.findText(cosName, pdResources, str2Find)).filter(Objects::nonNull).findFirst().orElse(null);
            }, exe)).collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
        }
        List<TextExtractAws> images = asyncs.stream().map(CompletableFuture::join).filter(Objects::nonNull).collect(Collectors.toList());
        closeAll(pdDoc, cosDoc);
        ResultTextAws result = new ResultTextAws();
        result.setResultDoc(toFile(images.stream().map(TextExtractAws::getImage).collect(Collectors.toList()), directory));
        result.setBolNums(images.stream().map(TextExtractAws::getBolNums).flatMap(Collection::stream).collect(Collectors.toList()));
        return result;
    }

    public TextExtractAws textImageFromPdf(String nameFile, String str2Find, int numPage) {
        PDDocument pdDoc = null;
        COSDocument cosDoc = null;
        File file = new File(nameFile);
        TextExtractAws response = null;

        try {
            PDFParser parser = new PDFParser(new RandomAccessFile(file, "r"));
            parser.parse();
            cosDoc = parser.getDocument();
            pdDoc = new PDDocument(cosDoc);
            PDPage page = pdDoc.getPage(numPage);
            PDResources pdResources = page.getResources();
            Stream<COSName> paralelStreamCos = StreamSupport.stream(pdResources.getXObjectNames().spliterator(), true);
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

    private TextExtractAws findText(COSName c, PDResources pdResources, String str2Find) {
        TextExtractAws textExtractAws = null;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDXObject pdx = pdResources.getXObject(c);
            if (pdx instanceof PDImageXObject) {
                ImageIO.write(((PDImageXObject) pdx).getImage(), "png", outputStream);
                textExtractAws = findTextImageAWS(str2Find, outputStream.toByteArray());
                if (textExtractAws.isContainText()) {
                    textExtractAws.setImage(outputStream.toByteArray());
                } else {
                    textExtractAws = null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return textExtractAws;
    }

    private List<String> getBolsNums(DetectTextResult result) {
        List<String> bolsFile = new ArrayList<>();
        try {
            List<TextDetection> textDetections = result.getTextDetections();
            textDetections.forEach(detection -> {
                Pattern pattern = Pattern.compile("(\\d{1,7})", Pattern.DOTALL);
                Matcher matcher = pattern.matcher(detection.getDetectedText());
                if (matcher.find() && matcher.group(1).length() >= 7)
                    bolsFile.add(matcher.group(1).trim());
            });
        } catch (AmazonRekognitionException e) {
            e.printStackTrace();
        }
        return bolsFile.stream().distinct().collect(Collectors.toList());
    }

    private TextExtractAws findTextImageAWS(String text2find, byte[] inputImage) {
        ByteBuffer imageByte = ByteBuffer.wrap(inputImage);
        List<String> array2Find = Arrays.asList(text2find.split("\n"));
        List<TextDetection> resultDetection;
        TextExtractAws textExtract = new TextExtractAws();
        DetectTextRequest request = new DetectTextRequest()
                .withImage(new Image().withBytes(imageByte));
        try {
            DetectTextResult result = rekognitionClient.detectText(request);
            List<TextDetection> textDetections = result.getTextDetections();
            resultDetection = textDetections.parallelStream()
                    .filter(detection ->
                            array2Find.stream()
                                    .anyMatch(s -> StringUtils.containsIgnoreCase(detection.getDetectedText(), s)))
                    .collect(Collectors.toList());
            textExtract.setBolNums(getBolsNums(result));
            textExtract.setContainText(!resultDetection.isEmpty());
        } catch (AmazonRekognitionException e) {
            e.printStackTrace();
        }
        return textExtract;
    }

    private String toFile(List<byte[]> images, String directory) {
        String fullPath = directory.concat("/result_" + new Date().getTime() + ".pdf");
        PDDocument doc = new PDDocument();
        try {
            images.parallelStream().forEach(imageBytes -> {
                PDPageContentStream contents = null;
                PDPage page = new PDPage();
                doc.addPage(page);
                try {
                    PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, imageBytes, "Image" + new Date().getTime());
                    contents = new PDPageContentStream(doc, page);
                    contents.drawImage(pdImage, -10, 50, (float) (pdImage.getWidth() / 4.5), (float) (pdImage.getHeight() / 4.4));
                } catch (Exception e) {
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
