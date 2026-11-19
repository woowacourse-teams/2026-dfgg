package dfgg.presentation.dto.request;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 큐에 따라 팀 인원과 챔피언 포지션을 검증한다. 위반은 {@code allies[0].position}처럼 필드 경로로 알린다. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TeamRosterValidator.class)
public @interface ValidTeamRoster {

    String message() default "팀 구성이 올바르지 않습니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
