package de.axonvisualizer.generator.init.guice;

import de.axonvisualizer.generator.json.provider.DataProvider;
import de.axonvisualizer.generator.json.provider.cytoscape.CytoscapeListener;
import de.axonvisualizer.generator.json.writer.JsonWriter;
import de.axonvisualizer.generator.json.writer.gson.GsonWriter;
import de.axonvisualizer.generator.logging.Log4JLogger;
import de.axonvisualizer.generator.logging.Logger;

import com.google.common.eventbus.EventBus;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class StandaloneModule extends AbstractModule {

   @Override
   protected void configure() {

      bind(JsonWriter.class).to(GsonWriter.class);
      bind(DataProvider.class).to(CytoscapeListener.class);
      bind(Logger.class).to(Log4JLogger.class);
      bindConstant().annotatedWith(Names.named("outputPath"))
            .to(outputPath);
      bindConstant().annotatedWith(Names.named("inputRoot"))
            .to(inputRoot);
      bind(EventBus.class).asEagerSingleton();
   }

   public StandaloneModule(final String inputRoot, final String outputPath) {
      this.inputRoot = inputRoot;
      this.outputPath = outputPath;
   }

   public StandaloneModule() {
   }

   private String inputRoot;
   private String outputPath;
}