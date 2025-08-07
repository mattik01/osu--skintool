module osu.skintool {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    
    requires org.slf4j;
    requires ch.qos.logback.classic;
    requires org.apache.commons.io;
    
    opens com.osuskin.tool to javafx.fxml;
    opens com.osuskin.tool.controller to javafx.fxml;
    opens com.osuskin.tool.model to com.fasterxml.jackson.databind;
    
    exports com.osuskin.tool;
}