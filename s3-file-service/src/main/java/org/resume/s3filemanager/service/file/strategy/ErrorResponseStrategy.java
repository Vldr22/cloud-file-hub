package org.resume.s3filemanager.service.file.strategy;

public interface ErrorResponseStrategy {

   String handle(String originalFileName, Exception e);

   boolean isSupport(Exception e);

}
