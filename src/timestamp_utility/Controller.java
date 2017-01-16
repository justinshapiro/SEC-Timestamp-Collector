package timestamp_utility;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class Controller implements Initializable {

    @FXML private TextField output_file_box;
    @FXML private TextArea cik_box;
    @FXML private TextArea filing_type_box;
    @FXML private TextArea date_box;
    @FXML private Button browse_button;
    @FXML private Button start_button;
    @FXML private Label status_indicator;
    @FXML public Label progress_area;
    @FXML private Hyperlink view_result;

    private File result_file = new File(System.getProperty("user.dir") + "\\timestamp_result.csv");

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);

        status_indicator.setText("Not Running");
        status_indicator.setTextFill(Color.web("#ff0000"));

        output_file_box.setText(System.getProperty("user.dir"));
        browse_button.setOnAction(event -> {
            DirectoryChooser directory_chooser = new DirectoryChooser();
            Stage stage = new Stage();
            stage.setTitle("Choose Directory");
            directory_chooser.setTitle("Choose Directory");
            File default_directory = new File(System.getProperty("user.dir"));
            directory_chooser.setInitialDirectory(default_directory);
            File selected_directory = directory_chooser.showDialog(stage);

            try {
                output_file_box.setText(selected_directory.toPath().toString());
            } catch (NullPointerException e) { /* do nothing */ }
        });

        start_button.setOnAction(event -> {
            if (output_file_box.getText().trim().isEmpty()) {
                alert.setContentText("The \"Output File Location\" field must contain a value.");
                alert.showAndWait();
            } else if (cik_box.getText().trim().isEmpty()) {
                alert.setContentText("The \"CIKs:\" box must contain a value.");
                alert.showAndWait();
            } else if (filing_type_box.getText().trim().isEmpty()) {
                alert.setContentText("The \"Disclosure type:\" box must contain a value.");
                alert.showAndWait();
            } else if (date_box.getText().trim().isEmpty()) {
                alert.setContentText("The \"Disclosure Accepted:\" box must contain a value.");
                alert.showAndWait();
            } else {
                String[] cik_data = cik_box.getText().split("\n");
                String[] filing_type_data = filing_type_box.getText().split("\n");
                String[] date_data = date_box.getText().split("\n");
                final String[][] data = {cik_data, filing_type_data, date_data};

                if (!((cik_data.length == filing_type_data.length) && (cik_data.length == date_data.length))) {
                    alert.setContentText("The number of elements in each box must be the same.");
                    alert.showAndWait();
                } else {
                    output_file_box.setDisable(true);
                    browse_button.setDisable(true);
                    start_button.setDisable(true);
                    cik_box.setDisable(true);
                    filing_type_box.setDisable(true);
                    date_box.setDisable(true);
                    progress_area.setDisable(false);
                    progress_area.setVisible(true);
                    status_indicator.setTextFill(Color.web("#aa7243"));
                    status_indicator.setText("Running...");
                    view_result.setVisible(false);


                    new Thread(new Task<Void>() {
                        @Override
                        protected Void call() throws Exception {
                            SECTimestampCollector collector_agent = new SECTimestampCollector();
                            collector_agent.init(output_file_box.getText(), data, Controller.this);
                            result_file = collector_agent.collect();

                            Platform.runLater(() -> {
                                output_file_box.setDisable(false);
                                browse_button.setDisable(false);
                                status_indicator.setTextFill(Color.web("#328332"));
                                status_indicator.setText("Job Finished");
                                start_button.setDisable(false);
                                view_result.setVisible(true);
                                cik_box.setDisable(false);
                                filing_type_box.setDisable(false);
                                date_box.setDisable(false);
                                progress_area.setDisable(true);
                                progress_area.setVisible(false);
                            });

                            return null;
                        }
                    }).start();
                }
            }
        });

        view_result.setOnAction(event -> {
            try {
                Desktop.getDesktop().open(result_file);
            } catch (IOException | IllegalArgumentException e) {
                alert.setContentText("Fatal: Result file from last run not found!");
                alert.showAndWait();
            }
        });
    }
}