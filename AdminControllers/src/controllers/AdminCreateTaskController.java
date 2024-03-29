package controllers;

import com.google.gson.Gson;
import http.HttpClientUtil;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.stage.DirectoryChooser;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import patterns.Patterns;
import tableItems.AdminCreateTaskTargetsTableItem;
import target.Graph;
import target.Target;
import task.*;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AdminCreateTaskController {
    //------------------------------------------------- Members ----------------------------------------------------//
    public TextArea taskDetailsOnTargetTextArea;
    private Graph graph;
    private String userName;
    private Gson gson;
    private Map<String, SimulationParameters> taskParametersMap = new HashMap<>();
    private SimulationParameters taskParameters;
    private final ObservableList<String> affectedTargetsOptions = FXCollections.observableArrayList();
    private final ObservableList<String> currentSelectedTargets = FXCollections.observableArrayList();
    private final String REQUIRED = "All required-for targets";
    private final String DEPENDED = "All depends-on targets";
    private final String SIMULATION ="Simulation";
    private final String COMPILATION ="Compilation";
    private final ObservableList<AdminCreateTaskTargetsTableItem> taskTargetDetailsList = FXCollections.observableArrayList();
    private ObservableList<String> allTargetsList;
    private String sourceCodeDirectoryPath = null;
    private String outputDirectoryPath = null;
    private TaskThread.TaskType taskType;

    //---------------------------------------------- FXML Members --------------------------------------------------//
    @FXML private BorderPane taskBorderPane;
    @FXML private ComboBox<String> taskSelection;
    @FXML private ComboBox<String> targetSelection;
    @FXML private ComboBox<String> affectedTargets;
    @FXML private ListView<String> currentSelectedTargetListView;
    @FXML private Label currentSelectedTargetLabel;
    @FXML private Label processingTimeLabel;
    @FXML private Label limitedPermanentLabel;
    @FXML private Label successRateLabel;
    @FXML private Label successRateWithWarnings;
    @FXML private RadioButton limitedRadioButton;
    @FXML private RadioButton permanentRadioButton;
    @FXML private Slider successRateSlider;
    @FXML private Slider successRateWithWarningsSlider;
    @FXML private TextField processingTimeTextField;
    @FXML private TextField successWithWarningRateText;
    @FXML private TextField successRateText;
    @FXML private Button ApplyParametersButton;
    @FXML private Button selectAllButton;
    @FXML private Button deselectAllButton;
    @FXML private Button addSelectedButton;
    @FXML private Label compilationSourceCodeLabel;
    @FXML private Label compilationOutputLabel;
    @FXML private Button toCompileButton;
    @FXML private Button compiledOutputButton;
    @FXML private Label sourceCodePathLabel;
    @FXML private Label outputPathLabel;
    @FXML private TableView<AdminCreateTaskTargetsTableItem> taskTargetDetailsTableView;
    @FXML private TableColumn<AdminCreateTaskTargetsTableItem, Integer> numberColumn;
    @FXML private TableColumn<AdminCreateTaskTargetsTableItem, String> targetNameColumn;
    @FXML private TableColumn<AdminCreateTaskTargetsTableItem, String> positionColumn;
    @FXML private Button removeSelectedButton;
    @FXML private Button clearTableButton;
    @FXML private Button CreateNewTaskButton;
    @FXML private TextField TaskNameTextField;

    //------------------------------------------------ Settings ----------------------------------------------------//
    public void initialize(String userName, Graph graph) {
        setUserName(userName);
        setGraph(graph);
        setTaskTypes();

        addListenersForSliders();
        addListenersForTextFields();
        addListenersForSelectedTargets();
        addListenersToButtons();
        addListenersForCompilationButtons();

        this.affectedTargetsOptions.addAll("none", this.DEPENDED, this.REQUIRED);
        this.affectedTargets.setItems(this.affectedTargetsOptions);
        this.gson = new Gson();

        initializeGraphDetails();
    }

    private void setTaskTargetDetailsTable() {
        int i = this.taskTargetDetailsTableView.getItems().size() + 1;
        String targetPosition, targetRuntimeStatus, targetResultStatus;
        AdminCreateTaskTargetsTableItem taskTargetInformation;
        ObservableList<AdminCreateTaskTargetsTableItem> tableList = this.taskTargetDetailsTableView.getItems();

        for(String currentTarget : this.currentSelectedTargetListView.getItems())
        {
            if(!targetExistedInTable(tableList, currentTarget)) {
                targetPosition = this.graph.getTarget(currentTarget).getTargetPosition().toString();
                taskTargetInformation = new AdminCreateTaskTargetsTableItem(i++, currentTarget, targetPosition);
                this.taskTargetDetailsList.add(taskTargetInformation);
            }
        }
        this.taskTargetDetailsTableView.setItems(this.taskTargetDetailsList);
    }

    private void setTaskTypes() {
        Set<String> taskTypesSet = new HashSet<>();

        this.graph.getTasksPricesMap().keySet().forEach(p-> taskTypesSet.add(p.toString()));
        ObservableList<String> taskSelectionList = FXCollections.observableArrayList(taskTypesSet);

        this.taskSelection.setItems(taskSelectionList);
    }

    private void initializeGraphDetails() {
        this.numberColumn.setCellValueFactory(new PropertyValueFactory<AdminCreateTaskTargetsTableItem, Integer>("number"));
        this.targetNameColumn.setCellValueFactory(new PropertyValueFactory<AdminCreateTaskTargetsTableItem, String>("targetName"));
        this.positionColumn.setCellValueFactory(new PropertyValueFactory<AdminCreateTaskTargetsTableItem, String>("position"));
    }

    private void addListenersForSelectedTargets() {
        //Enable/Disable incremental, selectAll, deselectAll button
        this.currentSelectedTargets.addListener((ListChangeListener<String>) c -> {
            boolean containAll = AdminCreateTaskController.this.currentSelectedTargets.containsAll(AdminCreateTaskController.this.allTargetsList);
            AdminCreateTaskController.this.selectAllButton.setDisable(containAll);
            AdminCreateTaskController.this.deselectAllButton.setDisable(!containAll);

            while (c.next()) {
                for (String remitem : c.getRemoved()) {
                    AdminCreateTaskController.this.currentSelectedTargetListView.getItems().remove(remitem);
                    AdminCreateTaskController.this.addSelectedButton.setDisable(true);
                }
                for (String additem : c.getAddedSubList()) {
                    AdminCreateTaskController.this.currentSelectedTargetListView.getItems().add(additem);
                    AdminCreateTaskController.this.addSelectedButton.setDisable(false);
                }
            }

            if(AdminCreateTaskController.this.currentSelectedTargets.isEmpty())
                AdminCreateTaskController.this.addSelectedButton.setDisable(true);
        });
    }

    private void addListenersForTextFields() {
        this.successRateText.textProperty().addListener((observable, oldValue, newValue) -> {
            try {
                if(newValue.equals(""))
                    return;

                if(Double.parseDouble(newValue) > 1.0)
                {
                    this.successRateText.setText(String.valueOf(1.0));
                    this.successRateSlider.setValue(1.0);
                }
                else if(Double.parseDouble(newValue) < 0.0)
                {
                    this.successRateText.setText(String.valueOf(0.0));
                    this.successRateSlider.setValue(0.0);
                }
                else
                    this.successRateSlider.setValue(Double.parseDouble(newValue));
            } catch(Exception ex)
            {
                ShowPopUp(Alert.AlertType.ERROR, "Invalid input", null,"Invalid input in task parameters!");
                this.successRateText.clear();
            }
        });

        this.successWithWarningRateText.textProperty().addListener((observable, oldValue, newValue) -> {
            try {
                if(newValue.equals(""))
                    return;

                if(Double.parseDouble(newValue) > 1.0)
                {
                    this.successRateWithWarningsSlider.setValue(1.0);
                    this.successWithWarningRateText.setText(String.valueOf(1.0));
                }
                else if(Double.parseDouble(newValue) < 0.0)
                {
                    this.successRateWithWarningsSlider.setValue(0.0);
                    this.successWithWarningRateText.setText(String.valueOf(0.0));
                }
                else
                    this.successRateWithWarningsSlider.setValue(Double.parseDouble(newValue));
            } catch(Exception ex)
            {
                ShowPopUp(Alert.AlertType.ERROR, "Invalid input", null, "Invalid input in task parameters!");
                this.successWithWarningRateText.clear();
            }
        });
    }

    private void addListenersForSliders() {
        this.successRateSlider.valueProperty().addListener((observable, oldValue, newValue) -> this.successRateText.setText(String.format("%.3f", newValue)));
        this.successRateWithWarningsSlider.valueProperty().addListener((observable, oldValue, newValue) -> this.successWithWarningRateText.setText(String.format("%.3f", newValue)));
    }

    private void addListenersToButtons() {
        this.taskTargetDetailsTableView.getItems().addListener(new ListChangeListener<AdminCreateTaskTargetsTableItem>() {
            @Override public void onChanged(Change<? extends AdminCreateTaskTargetsTableItem> c) {
                AdminCreateTaskController.this.removeSelectedButton.setDisable(c.getList().isEmpty());
                // TaskController.this.clearTableButton.setDisable(c.getList().isEmpty());
            }
        });
    }

    private void addListenersForCompilationButtons() {
        this.taskSelection.valueProperty().addListener(new ChangeListener<String>() {
            @Override public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                setVisibilityOfTask(AdminCreateTaskController.this.taskSelection.getValue().equals(AdminCreateTaskController.this.COMPILATION));
            }
        });
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
        setAllTargetsList();
        setTaskTargetDetailsTable();
    }

    //----------------------------------------------- Simulation ---------------------------------------------------//
    @FXML void ApplyParametersToTask(ActionEvent event) {
        this.taskParameters = getSimulationTaskParametersFromUser();

        if(this.taskParameters.getProcessingTime() != null) // Checking only on the processing time, because other parameters are already initialized
            this.CreateNewTaskButton.setDisable(false);
    }

    private void setForSimulationTask(boolean flag) {
        this.processingTimeLabel.setDisable(flag);
        this.limitedPermanentLabel.setDisable(flag);
        this.successRateLabel.setDisable(flag);
        this.successRateWithWarnings.setDisable(flag);

        this.processingTimeTextField.setDisable(flag);
        this.limitedRadioButton.setDisable(flag);
        this.permanentRadioButton.setDisable(flag);

        this.successRateSlider.setDisable(flag);
        this.successRateWithWarningsSlider.setDisable(flag);

        this.successRateText.setDisable(flag);
        this.successWithWarningRateText.setDisable(flag);
        this.ApplyParametersButton.setDisable(flag);
    }

    public SimulationParameters getSimulationTaskParametersFromUser() {
        SimulationParameters taskParameters = new SimulationParameters();
        Duration processingTime;
        long timeInMS;
        boolean isRandom;
        double successRate, successWithWarnings;

        try {
            timeInMS = Integer.parseInt(this.processingTimeTextField.getText());
            processingTime = Duration.of(timeInMS, ChronoUnit.MILLIS);

            isRandom = this.limitedRadioButton.isSelected();
            successRate = this.successRateSlider.getValue();
            successWithWarnings = this.successRateWithWarningsSlider.getValue();

            taskParameters.setProcessingTime(processingTime);
            taskParameters.setRandom(isRandom);
            taskParameters.setSuccessRate(successRate);
            taskParameters.setSuccessWithWarnings(successWithWarnings);
        }
        catch(Exception ex) {ShowPopUp(Alert.AlertType.ERROR, "Invalid Parameters", null,"Invalid input in parameters.");}

        return taskParameters;
    }

    private void applyTaskParametersForAllTargets(SimulationParameters taskParameters) {
        this.taskParametersMap = new HashMap<>();
        SimulationParameters currTaskParameters;
        Duration processingTime;
        long randomTime;
        double successRate = taskParameters.getSuccessRate(), successRateWithWarnings = taskParameters.getSuccessWithWarnings();
        Boolean isRandom = taskParameters.isRandom();

        //permanent time for all targets
        if(!isRandom)
        {
            for(Target target : this.graph.getGraphTargets().values())
                this.taskParametersMap.put(target.getTargetName(), taskParameters);

            return;
        }

        //Random time for each target
        for(Target target : this.graph.getGraphTargets().values())
        {
            processingTime = taskParameters.getProcessingTime();
            randomTime = (long)(Math.random() * (processingTime.toMillis())) + 1;
            processingTime = (Duration.of(randomTime, ChronoUnit.MILLIS));

            currTaskParameters = new SimulationParameters(processingTime, isRandom, successRate, successRateWithWarnings);
            this.taskParametersMap.put(target.getTargetName(), currTaskParameters);
        }
    }

    //---------------------------------------------- Compilation ---------------------------------------------------//
    @FXML void chooseSourceCodeDirectoryToCompile(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        File sourceCodeDirectory = directoryChooser.showDialog(this.taskBorderPane.getParent().getScene().getWindow());
        if(sourceCodeDirectory != null)
        {
            this.sourceCodeDirectoryPath = sourceCodeDirectory.toPath().toString();
            this.sourceCodePathLabel.setText("Source Code Path: " + sourceCodeDirectory.getAbsolutePath());
        }
    }

    @FXML void chooseOutputDirectory(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        File outputDirectory = directoryChooser.showDialog(this.taskBorderPane.getParent().getScene().getWindow());
        if(outputDirectory != null)
        {
            this.outputDirectoryPath = outputDirectory.toPath().toString();
            this.outputPathLabel.setText("Output Path: " + outputDirectory.getAbsolutePath());
        }
    }

    //-------------------------------------------- Choosing Targets ------------------------------------------------//
    private void setAllTargetsList() {
        int i = 0;
        this.allTargetsList = FXCollections.observableArrayList();

        for(Target currentTargetName : this.graph.getGraphTargets().values())
            this.allTargetsList.add(i++, currentTargetName.getTargetName());

        final SortedList<String> sorted = this.allTargetsList.sorted();
        this.targetSelection.getItems().addAll(sorted);
    }

    @FXML void affectedTargetsPressed(ActionEvent event) {
        Set<String> affectedTargetsSet = null;

        switch (this.affectedTargets.getValue())
        {
            case DEPENDED:
            {
                affectedTargetsSet = this.graph.getTarget(this.targetSelection.getValue()).getAllDependsOnTargets();
                break;
            }
            case REQUIRED:
            {
                affectedTargetsSet = this.graph.getTarget(this.targetSelection.getValue()).getAllRequiredForTargets();
                break;
            }
            default:
                break;
        }

        if(affectedTargetsSet != null)
        {
            this.currentSelectedTargets.clear();
            this.currentSelectedTargets.add(this.targetSelection.getValue());
            this.currentSelectedTargets.addAll(affectedTargetsSet);
        }
    }

    @FXML void addSelectedTargetsToTable(ActionEvent event) {
        setTaskTargetDetailsTable();

        if(this.taskType.equals(TaskThread.TaskType.Compilation))
        {
            if(this.outputDirectoryPath != null && this.sourceCodeDirectoryPath != null)
                this.CreateNewTaskButton.setDisable(false);
        }
        else
        {
            if(this.taskParameters != null)
                this.CreateNewTaskButton.setDisable(false);
        }
        this.clearTableButton.setDisable(false);
    }

    @FXML void selectAllPressed(ActionEvent event) {
        this.currentSelectedTargets.clear();
        this.graph.getGraphTargets().values().forEach(targetName -> this.currentSelectedTargets.add(targetName.getTargetName()));
    }

    private boolean targetExistedInTable(ObservableList<AdminCreateTaskTargetsTableItem> tableList, String currentTargetName) {
        for(AdminCreateTaskTargetsTableItem currInfo : tableList)
        {
            if(currInfo.getTargetName().equals(currentTargetName))
                return true;
        }
        return false;
    }

    @FXML void deselectAllPressed(ActionEvent event) {
        this.currentSelectedTargets.clear();
    }

    @FXML void removeSelectedRowFromTable(ActionEvent event) {
        if(this.taskTargetDetailsTableView.getItems().size()>0)
        {
            AdminCreateTaskTargetsTableItem chosenTarget = this.taskTargetDetailsTableView.getSelectionModel().getSelectedItem();
            if(chosenTarget!=null) {
                int index = chosenTarget.getNumber() - 1, size = this.taskTargetDetailsTableView.getItems().size();

                this.taskTargetDetailsTableView.getItems().remove(chosenTarget);

                while (size - 1 > index) {
                    chosenTarget = this.taskTargetDetailsTableView.getItems().get(index);
                    chosenTarget.setNumber(++index);
                }

                if (size - 1 == 0) {
//                    this.runButton.setDisable(true);
                    this.clearTableButton.setDisable(false);
                }
            }
        }
        if(this.taskTargetDetailsTableView.getItems().isEmpty())
        {
            this.removeSelectedButton.setDisable(true);
            this.clearTableButton.setDisable(true);
        }
    }

    @FXML void targetSelectionPressed(ActionEvent event) {
        this.affectedTargets.setDisable(false);

        this.currentSelectedTargets.clear();
        this.currentSelectedTargets.add(this.targetSelection.getValue());

        if(this.affectedTargets.getValue() != null)
            affectedTargetsPressed(event);
    }

    @FXML void ClearTable(ActionEvent event) {
        this.taskTargetDetailsTableView.getItems().clear();
        this.CreateNewTaskButton.setDisable(true);
        this.clearTableButton.setDisable(true);
        this.removeSelectedButton.setDisable(true);
    }

    //---------------------------------------------- Create Task ---------------------------------------------------//
    @FXML void CreateNewTaskButtonPressed(ActionEvent event) {
        String taskName = this.TaskNameTextField.getText();
        String uploader = this.userName;
        String graphName = this.graph.getGraphName();
        Set<String> targets = new HashSet<>();
        String taskTypeRequest = null;
        String stringObject = null;

        for (AdminCreateTaskTargetsTableItem curr : this.taskTargetDetailsTableView.getItems())
            targets.add(curr.getTargetName());

        if(this.taskSelection.getValue().equals("Simulation"))
        {
            Integer pricing = this.graph.getTasksPricesMap().get(Graph.TaskType.Simulation);
            SimulationParameters parameters = this.taskParameters;

            SimulationTaskInformation taskInfo = new SimulationTaskInformation(taskName, uploader,
                    graphName, targets, pricing, parameters);
            taskTypeRequest = "Simulation";
            stringObject = this.gson.toJson(taskInfo);
        }
        else if(this.taskSelection.getValue().equals("Compilation"))
        {
            Integer pricing = this.graph.getTasksPricesMap().get(Graph.TaskType.Compilation);
            CompilationParameters parameters = new CompilationParameters(this.sourceCodeDirectoryPath, this.outputDirectoryPath);

            CompilationTaskInformation taskInfo = new CompilationTaskInformation(taskName, uploader, graphName, targets, pricing, parameters);
            taskTypeRequest = "Compilation";
            stringObject = this.gson.toJson(taskInfo);
        }

        if(!checkForValidCreationOfTask())
            return;

        uploadTaskToServer(stringObject, taskTypeRequest);
    }

    private void uploadTaskToServer(String stringObject, String taskTypeRequest) {
        RequestBody body = RequestBody.create(stringObject, MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(Patterns.TASK)
                .post(body).addHeader(taskTypeRequest, taskTypeRequest)
                .build();

        HttpClientUtil.runAsyncWithRequest(request, new Callback() {
            @Override public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Platform.runLater(()-> ShowPopUp(Alert.AlertType.ERROR, "Error in uploading task!", null, e.getMessage()));
            }

            @Override public void onResponse(@NotNull Call call, @NotNull Response response) {
                if(response.code() >= 200 && response.code() < 300)
                    Platform.runLater(() -> ShowPopUp(Alert.AlertType.INFORMATION, "Task uploaded successfully!", null, response.header("message")));
                else
                    Platform.runLater(() -> ShowPopUp(Alert.AlertType.ERROR, "Error in uploading task!", null, response.header("message")));
            }
        });
    }

    private Boolean checkForValidCreationOfTask() {
        String errorMessage = "";

        if(this.taskTargetDetailsTableView.getItems().isEmpty())
            errorMessage = "You have to choose at least 1 target first!";
        else if(this.TaskNameTextField.getText().trim().equals(""))
            errorMessage = "You have to name your task!";
        else if(this.taskType.equals(TaskThread.TaskType.Simulation))
        {
            if(this.taskParameters == null)
                errorMessage = "You have to apply the parameters for the task first!";
        }
        else //Compilation task
        {
            if(this.sourceCodeDirectoryPath == null || this.outputDirectoryPath == null)
                errorMessage = "Please choose directories for compilation task!";
        }

        if(!errorMessage.equals(""))
        {
            ShowPopUp(Alert.AlertType.ERROR, "Can't Start Task", null, errorMessage);
            return false;
        }
        return true;
    }

    //------------------------------------------------ Visibility -----------------------------------------------------//
    private void setVisibilityOfTask(boolean flag) {
        //Compilation
        this.compilationOutputLabel.setVisible(flag);
        this.compilationSourceCodeLabel.setVisible(flag);
        this.toCompileButton.setVisible(flag);
        this.compiledOutputButton.setVisible(flag);
        this.sourceCodePathLabel.setVisible(flag);
        this.outputPathLabel.setVisible(flag);

        //Simulation
        this.processingTimeLabel.setVisible(!flag);
        this.processingTimeTextField.setVisible(!flag);
        this.successRateSlider.setVisible(!flag);
        this.successRateWithWarningsSlider.setVisible(!flag);
        this.limitedPermanentLabel.setVisible(!flag);
        this.limitedRadioButton.setVisible(!flag);
        this.permanentRadioButton.setVisible(!flag);
        this.successWithWarningRateText.setVisible(!flag);
        this.successRateText.setVisible(!flag);
        this.ApplyParametersButton.setVisible(!flag);
        this.successRateLabel.setVisible(!flag);
        this.successRateWithWarnings.setVisible(!flag);
    }

    private void disableButtons(Boolean flag) {
        this.targetSelection.setDisable(flag);
        this.affectedTargets.setDisable(flag);
        this.currentSelectedTargetLabel.setDisable(flag);
        this.currentSelectedTargetListView.setDisable(flag);
        this.selectAllButton.setDisable(flag);
    }

    //------------------------------------------------ General -----------------------------------------------------//
    private void ShowPopUp(Alert.AlertType alertType, String title, String header, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    //----------------------------------------------- Not Used -----------------------------------------------------//
    @FXML void taskSelectionPressed(ActionEvent event) {
        if(!this.taskSelection.getSelectionModel().isEmpty())
        {
            setForSimulationTask(!this.taskSelection.getValue().equals(this.SIMULATION));
            disableButtons(false);
        }

        this.taskType = this.taskSelection.getValue().equals("Simulation") ? TaskThread.TaskType.Simulation : TaskThread.TaskType.Compilation;
    }

    private void enableTargetInfoTextArea(boolean flag) {
        this.taskDetailsOnTargetTextArea.setVisible(flag);
        this.taskDetailsOnTargetTextArea.setDisable(!flag);
    }

    private Set<String> setCurrentRunTargets() {
        Set<String> currentRunTargets = new HashSet<>();

        for(AdminCreateTaskTargetsTableItem curr : this.taskTargetDetailsTableView.getItems())
            currentRunTargets.add(curr.getTargetName());

        return currentRunTargets;
    }

    private void disableTaskOptions(Boolean flag) {
        this.CreateNewTaskButton.setDisable(flag);

        this.currentSelectedTargetLabel.setDisable(flag);
        this.taskSelection.setDisable(flag);
        this.targetSelection.setDisable(flag);
        this.affectedTargets.setDisable(flag);

        this.currentSelectedTargetListView.setDisable(flag);
        this.selectAllButton.setDisable(flag);
        this.deselectAllButton.setDisable(flag);
        this.addSelectedButton.setDisable(flag);

        setForSimulationTask(flag);
    }
}