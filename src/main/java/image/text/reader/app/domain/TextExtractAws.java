package image.text.reader.app.domain;

import java.io.Serializable;
import java.util.List;

public class TextExtractAws implements Serializable {

    private List<String> bolNums;
    private byte[] image;
    private boolean containText;

    public List<String> getBolNums() {
        return bolNums;
    }

    public void setBolNums(List<String> bolNums) {
        this.bolNums = bolNums;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    public byte[] getImage() {
        return image;
    }

    public boolean isContainText() {
        return containText;
    }

    public void setContainText(boolean containText) {
        this.containText = containText;
    }
}
