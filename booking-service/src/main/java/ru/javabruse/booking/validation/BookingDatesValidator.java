package ru.javabruse.booking.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import ru.javabruse.booking.dto.BookingRequest;

public class BookingDatesValidator implements ConstraintValidator<ValidBookingDates, BookingRequest> {
    
    @Override
    public void initialize(ValidBookingDates constraintAnnotation) {
    }
    
    @Override
    public boolean isValid(BookingRequest request, ConstraintValidatorContext context) {
        if (request == null || request.getStartDate() == null || request.getEndDate() == null) {
            return true; // Let @NotNull handle null values
        }
        
        return request.getEndDate().isAfter(request.getStartDate());
    }
}
