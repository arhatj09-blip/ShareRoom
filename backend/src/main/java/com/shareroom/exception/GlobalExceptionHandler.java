package com.shareroom.exception;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
 @ExceptionHandler(ApiException.class) ResponseEntity<Map<String,String>> api(ApiException e){return ResponseEntity.status(e.status()).body(Map.of("error",e.getMessage()));}
 @ExceptionHandler({IllegalArgumentException.class,MaxUploadSizeExceededException.class})
 ResponseEntity<Map<String,String>> bad(Exception e){return ResponseEntity.badRequest().body(Map.of("error",e instanceof MaxUploadSizeExceededException?"File exceeds 25 MB":e.getMessage()));}
 @ExceptionHandler(Exception.class) ResponseEntity<Map<String,String>> server(Exception e){return ResponseEntity.status(500).body(Map.of("error","Unexpected server error"));}
}
