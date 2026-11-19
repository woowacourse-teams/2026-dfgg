package dfgg.domain.feedback;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;

@Entity
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id")
    private Long id;
    private LocalDate date;
    private String content;

    public Feedback() {

    }

    private Feedback(LocalDate date, String content) {
        this.date = date;
        this.content = content;
    }

    public static Feedback create(LocalDate date, String content) {
        return new Feedback(date, content);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getContent() {
        return content;
    }
}
