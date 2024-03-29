package userInterface;

import controllers.AdminLoginController;
import http.HttpClientUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import paths.BodyComponentsPaths;
import patterns.Patterns;

import java.awt.*;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdminMain extends Application
{
    @Override public void start(Stage primaryStage) throws Exception {
        Logger.getLogger(OkHttpClient.class.getName()).setLevel(Level.FINE);
        primaryStage.setTitle("G.P.U.P Login");
        FXMLLoader loader = new FXMLLoader(getClass().getResource(BodyComponentsPaths.LOGIN));
        Parent root = loader.load();
        primaryStage.setScene(new Scene(root, 520,220));
        primaryStage.getScene().getStylesheets().add(BodyComponentsPaths.LOGIN_THEME);

        //Set the Stage
        AdminLoginController adminLoginController = loader.getController();
        adminLoginController.initialize(primaryStage);

        primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Close confirmation");
                alert.setHeaderText("Are you sure you want to exit?");

                alert.initOwner(primaryStage);
                Toolkit.getDefaultToolkit().beep();
                Optional<ButtonType> result = alert.showAndWait();
                if(result.get() == ButtonType.OK) {
                    logout(adminLoginController.getCurrentUser());
                    Platform.exit();
                }
                event.consume();
            }
        });

        //show the stage
        primaryStage.show();
    }

    private void logout(String userName) {
        if(userName == null)
            return;

        String finalUrl = HttpUrl
                .parse(Patterns.LOCAL_HOST + Patterns.LOGOUT)
                .newBuilder()
                .addQueryParameter("username",userName)
                .build()
                .toString();

        HttpClientUtil.runAsync(finalUrl, "DELETE", null, new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                System.out.println("failed to logout user");
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                System.out.println("user logged out");
            }
        });
    }

    public static void main(String[] args)
    {
        launch(AdminMain.class);
    }
}