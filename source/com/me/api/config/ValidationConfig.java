package com.me.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.Validator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * Configuration for validation
 * This conditionally sets up the validator only if the required classes are available
 */
@Configuration
public class ValidationConfig {

    /**
     * Create a validator bean only if hibernate validator is available
     */
    @Bean
    @ConditionalOnClass(name = {"javax.validation.Validator", "org.hibernate.validator.HibernateValidator"})
    @ConditionalOnMissingBean(Validator.class)
    public Validator validator() {
        try {
            LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
            // Try to load validator-specific properties if needed
            validator.afterPropertiesSet();
            return validator;
        } catch (Exception e) {
            // If validation setup fails, provide a fallback validator that does nothing
            return new FallbackValidator();
        }
    }
    
    /**
     * Simple fallback validator that does nothing.
     * This ensures the API still runs even if validation dependencies are missing.
     */
    private static class FallbackValidator implements Validator {
        @Override
        public boolean supports(Class<?> clazz) {
            return true;
        }

        @Override
        public void validate(Object target, org.springframework.validation.Errors errors) {
            // Do nothing - validation is skipped
        }
    }
}
