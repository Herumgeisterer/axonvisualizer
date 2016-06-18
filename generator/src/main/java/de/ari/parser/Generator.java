package de.ari.parser;

import de.ari.data.Aggregate;
import de.ari.data.AxonData;
import de.ari.data.CommandHandler;
import de.ari.data.EventHandler;
import de.ari.data.EventListener;
import de.ari.json.JsonProvider;
import de.ari.json.cytoscape.CytoscapeJsonProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.JavaUnit;
import org.jboss.forge.roaster.model.Type;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.source.ParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@SuppressWarnings("unused")
@Component
public class Generator {

   public static final String EVENT_HANDLER_ANNOTATION_NAME = "EventHandler";
   public static final String ABSTRACT_ANNOTATED_AGGREGATE_ROOT = "AbstractAnnotatedAggregateRoot";
   public static final String SAGA_EVENT_HANDLER_NAME = "SagaEventHandler";
   public static final String EVENT_SOURCING_HANDLER_NAME = "EventSourcingHandler";

   public static final String INPUT_ROOT = "/Development/wee/wee-backend/";
   public static final String OUTPUT_PATH = "/Development/axonvisualizer/webapp/app/data";

   Logger LOGGER = Logger.getLogger(Generator.class);

   private AxonData.AxonDataBuilder dataBuilder = AxonData.builder();
   private List<EventHandler> eventHandlers = new ArrayList<>();
   private JsonProvider jsonProvider;

   @PostConstruct
   public void init() throws IOException {

      final Stream<Path> walk = Files.walk(new File(INPUT_ROOT).toPath())
            .filter(p -> p.getFileName()
                  .toString()
                  .endsWith(".java"));

      walk.forEach(path -> {
         FileInputStream fileInputStream = null;
         try {
            fileInputStream = new FileInputStream(path.toFile());
         } catch (FileNotFoundException e) {
            throw new RuntimeException("Could not find file: " + path.toFile());
         }

         JavaUnit unit = Roaster.parseUnit(fileInputStream);

         if (!unit.getGoverningType()
               .isClass()) {
            return;
         }

         JavaClassSource myClass = unit.getGoverningType();

         if (myClass.isAbstract()) {
            return;
         }

         final String superType = myClass.getSuperType();
         if (superType.contains(ABSTRACT_ANNOTATED_AGGREGATE_ROOT) || superType.contains("AbstractAnnotatedEntity")) {
            final Aggregate aggregate = getAggregate(myClass);
            dataBuilder.aggregate(aggregate);
         }

         final EventListener eventListener = getEventListener(myClass);
         if (eventListener != null) {
            dataBuilder.eventListener(eventListener);
         }
      });

      File outPutFile = new File(OUTPUT_PATH + "/output.json");

      if (outPutFile.exists()) {
         final boolean deleted = outPutFile.delete();
         if (!deleted) {
            LOGGER.warn("Could not delete old file. Output could be in inconsistent state.");
         }
      }

      jsonProvider = new CytoscapeJsonProvider();

      try (Writer writer = new FileWriter(outPutFile)) {
         final String json = jsonProvider.getJson(dataBuilder.build());
         writer.write(json);
         writer.close();
      }
   }

   private EventListener getEventListener(final JavaClassSource klass) {

      final List<MethodSource<JavaClassSource>> methods = klass.getMethods();

      final List<EventHandler> eventHandlers = new ArrayList<>();

      for (MethodSource<JavaClassSource> method : methods) {
         for (AnnotationSource<JavaClassSource> annotation : method.getAnnotations()) {
            if (isEventHandlingMethod(annotation.getName())) {
               eventHandlers.add(getEventHandler(method));
            }
         }
      }

      if (eventHandlers.isEmpty()) {
         return null;
      }

      return EventListener.builder()
            .name(klass.getName())
            .eventHandlers(eventHandlers)
            .build();
   }

   private boolean isEventHandlingMethod(final String annotationName) {
      final boolean isEventHandler = annotationName.equals(EVENT_HANDLER_ANNOTATION_NAME);
      final boolean isEventSourcingHandler = annotationName.equals(EVENT_SOURCING_HANDLER_NAME);
      final boolean isSagaEventHandler = annotationName.equals(SAGA_EVENT_HANDLER_NAME);
      return isEventHandler || isEventSourcingHandler || isSagaEventHandler;
   }

   private EventHandler getEventHandler(final MethodSource<JavaClassSource> method) {
      final String eventTypeName = method.getParameters()
            .get(0)
            .getType()
            .getName();

      String listenerType = null;

      for (AnnotationSource<JavaClassSource> annotation : method.getAnnotations()) {
         final String annotationName = annotation.getName();

         if (annotationName.equals(EVENT_SOURCING_HANDLER_NAME)) {
            listenerType = "Aggregate";
            break;
         }

         if (annotationName.equals(EVENT_HANDLER_ANNOTATION_NAME)) {
            listenerType = "Listener";
            break;
         }

         if (annotationName.equals(SAGA_EVENT_HANDLER_NAME)) {
            listenerType = "Saga";
            break;
         }
      }

      return EventHandler.builder()
            .eventType(eventTypeName)
            .type(listenerType)
            .build();
   }

   private Aggregate getAggregate(final JavaClassSource myClass) {
      final String aggregateName = myClass.getName();

      final Aggregate.AggregateBuilder aggregateBuilder = Aggregate.builder()
            .name(aggregateName);

      final List<MethodSource<JavaClassSource>> methods = myClass.getMethods();

      for (MethodSource<JavaClassSource> method : methods) {
         if (!isCommandHandler(method)) {
            continue;
         }

         final String body = method.getBody();

         final ParameterSource<JavaClassSource> command = method.getParameters()
               .get(0);

         final Type<JavaClassSource> commandType = command.getType();

         final CommandHandler.CommandHandlerBuilder commandHandlerBuilder = CommandHandler.builder()
               .command(commandType.getName());

         final List<String> appliedEvents = getAppliedEvents(body);

         final CommandHandler commandHandler = commandHandlerBuilder.events(appliedEvents)
               .build();

         aggregateBuilder.commandHandler(commandHandler);
      }

      return aggregateBuilder.build();
   }

   private boolean isCommandHandler(final MethodSource<JavaClassSource> method) {
      final List<AnnotationSource<JavaClassSource>> annotations = method.getAnnotations();

      for (AnnotationSource<JavaClassSource> annotation : annotations) {
         if (annotation.getName()
               .equals("CommandHandler")) {
            return true;
         }
      }

      return false;
   }

   private List<String> getAppliedEvents(final String body) {
      List<String> appliedEvents = new ArrayList<>();

      final int appliedEventsCount = getAppliedEventsCount(body);

      int applySearchStart = 0;
      int builderSearchStart = 0;

      for (int i = 0; i < appliedEventsCount; i++) {
         final int applyIndex = body.indexOf("apply(", applySearchStart);
         final int builderIndex = body.indexOf(".builder", builderSearchStart);

         final int eventTypeNameStart = applyIndex + "apply(".length();
         final int builderOccurenceEnd = builderIndex + ".builder".length();

         final String event = body.substring(eventTypeNameStart, builderIndex);
         appliedEvents.add(event);

         applySearchStart = eventTypeNameStart;
         builderSearchStart = builderOccurenceEnd;
      }
      return appliedEvents;
   }

   private int getAppliedEventsCount(final String body) {
      return StringUtils.countOccurrencesOf(body, "apply");
   }
}