package image.text.reader.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ImagePdfController {

    @GetMapping("/")
    public ResponseEntity<String> hello() {
        return ResponseEntity.ok("TODO OK");
    }

}
