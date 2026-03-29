package com.jamshedsql;

import com.jamshedsql.controllers.MainController;
import com.jamshedsql.launcher.BackendLauncher;
import com.jamshedsql.services.ApiService;
import com.jamshedsql.util.FirstLaunchShortcut;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class MainApp extends Application {

    public static final String DEFAULT_API = "http://127.0.0.1:8765";

    @Override
    public void init() {
        BackendLauncher.startEmbeddedBackendIfNeeded();
    }

    @Override
    public void stop() throws Exception {
        BackendLauncher.shutdownEmbeddedBackend();
        super.stop();
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/com/jamshedsql/views/main.fxml")));
        Parent root = loader.load();
        MainController c = loader.getController();
        c.setApiService(new ApiService(System.getProperty("jsql.api", DEFAULT_API)));

        Scene scene = new Scene(root, 1180, 760);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/com/jamshedsql/styles.css")).toExternalForm());

        stage.setTitle("JSQL — JamshedSQL ⚡ Catch your data!");
        stage.setScene(scene);
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.show();
        FirstLaunchShortcut.offerAfterShown(stage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
