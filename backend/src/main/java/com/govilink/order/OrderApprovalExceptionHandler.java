package com.govilink.order;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
        assignableTypes = OrderApprovalController.class
)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OrderApprovalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidApproval(
            IllegalArgumentException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );

        problem.setTitle("Invalid approval details");

        return problem;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail approvalConflict(
            IllegalStateException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );

        problem.setTitle("Order approval conflict");

        return problem;
    }

    @ExceptionHandler({
            OptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class
    })
    public ProblemDetail concurrentChange(
            RuntimeException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Stock or quotation details are being changed. Reload before trying again."
        );

        problem.setTitle("Concurrent change");

        return problem;
    }
}