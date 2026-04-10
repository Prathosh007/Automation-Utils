package com.me.api.swagger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A no-op annotation that replaces Swagger annotations when Swagger is not available.
 * This allows the code to compile and run without the Swagger dependencies.
 */
public class OptionalSwagger {

    // Used in place of @Tag
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    public @interface Tag {
        String name() default "";
        String description() default "";
    }
    
    // Used in place of @Operation
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.METHOD)
    public @interface Operation {
        String summary() default "";
        String description() default "";
    }
    
    // Used in place of @ApiResponses
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.METHOD)
    public @interface ApiResponses {
        ApiResponse[] value() default {};
    }
    
    // Used in place of @ApiResponse
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.METHOD)
    public @interface ApiResponse {
        String responseCode() default "200";
        String description() default "";
    }
    
    // Used in place of @Parameter
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.PARAMETER)
    public @interface Parameter {
        String description() default "";
        boolean required() default false;
    }
}
