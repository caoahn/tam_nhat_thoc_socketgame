package com.example.gamesocket;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.function.Consumer;

public class ChatWindow extends Stage {
    private TextArea chatArea;
    private TextField messageField;

    public ChatWindow(String currentUser, String recipient, Consumer<String> messageSender) {
        setTitle("Trò chuyện với " + recipient);
        setResizable(false);

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10));

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        messageField = new TextField();
        messageField.setPromptText("Nhập tin nhắn...");

        Button sendButton = new Button("Gửi");

        Runnable sendMessageAction = () -> {
            String message = messageField.getText().trim();
            if (!message.isEmpty()) {
                messageSender.accept(message);
                appendMessage(currentUser + " (Bạn): " + message);
                messageField.clear();
            }
        };

        sendButton.setOnAction(e -> sendMessageAction.run());
        messageField.setOnAction(e -> sendMessageAction.run());

        HBox inputBox = new HBox(5, messageField, sendButton);
        HBox.setHgrow(messageField, Priority.ALWAYS);

        layout.getChildren().addAll(chatArea, inputBox);

        Scene scene = new Scene(layout, 400, 300);
        setScene(scene);
    }

    public void appendMessage(String message) {
        chatArea.appendText(message + "\n");
    }
}