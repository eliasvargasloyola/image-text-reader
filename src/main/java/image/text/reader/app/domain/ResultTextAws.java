package image.text.reader.app.domain;

import java.io.Serializable;
import java.util.List;

public class ResultTextAws implements Serializable {

    List<String> bolNums;
    String resultDoc;

    public List<String> getBolNums() {
        return bolNums;
    }

    public void setBolNums(List<String> bolNums) {
        this.bolNums = bolNums;
    }

    public String getResultDoc() {
        return resultDoc;
    }

    public void setResultDoc(String resultDoc) {
        this.resultDoc = resultDoc;
    }
}
