package de.visualaxon.generator.logging;

import de.visualaxon.generator.VisualAxonApplication;

public class Log4JLogger implements Logger {

   private final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(VisualAxonApplication.class);

   @Override
   public void info(final String message) {
      logger.info(message);
   }

   @Override
   public void error(final String message) {
      logger.error(message);
   }

   @Override
   public void error(final String message, final Throwable error) {
      logger.error(message, error);
   }

   @Override
   public void error(final Throwable error) {
      logger.error(error);
   }

   @Override
   public void debug(final String message) {
      logger.debug(message);
   }
}
