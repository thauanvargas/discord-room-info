import entities.Player;
import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.extra.tools.GAsync;
import gearth.extensions.parsers.*;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.Timer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@ExtensionInfo(
        Title = "Discord Room Info",
        Description = "Webhook for passing room user info to Discord",
        Version = "1.2",
        Author = "Thauan"
)

public class DCRoomInfo extends ExtensionForm {
    public static DCRoomInfo RUNNING_INSTANCE;
    public Label labelInfo;
    public String host;
    public GAsync gAsync;
    public int habboIndex = -1;
    public int habboId;
    public String habboName;
    public Button enableButton;
    public TextField webhookTextField;
    public TextField delayTextField;
    public Button testWebHookButton;
    public Label roomInfoLabel;
    public ListView<String> playerListView;
    public TextField botNameTextField;
    public List<Player> playerList = new ArrayList<>();
    public Timer timerSendPayload;
    public boolean enabled = false;
    public int roomId;
    public String roomName;
    public long startTime;
    public Timer updateTimer;
    public Label webhookTimerLabel;
    public Label extensionName;
    public TextField botImageTextField;

    private static final TreeMap<String, String> codeToDomainMap = new TreeMap<>();
    static {
        codeToDomainMap.put("br", ".com.br");
        codeToDomainMap.put("de", ".de");
        codeToDomainMap.put("es", ".es");
        codeToDomainMap.put("fi", ".fi");
        codeToDomainMap.put("fr", ".fr");
        codeToDomainMap.put("it", ".it");
        codeToDomainMap.put("nl", ".nl");
        codeToDomainMap.put("tr", ".com.tr");
        codeToDomainMap.put("us", ".com");
    }


    @Override
    protected void onStartConnection() {

        new Thread(() -> {
            sendToServer(new HPacket("InfoRetrieve", HMessage.Direction.TOSERVER));
            sendToServer(new HPacket("AvatarExpression", HMessage.Direction.TOSERVER, 0));
            sendToServer(new HPacket("GetHeightMap", HMessage.Direction.TOSERVER));
            System.out.println("Extension Loaded.");
        }).start();

        gAsync = new GAsync(this);
    }

    @Override
    protected void onShow() {
    }

    @Override
    protected void initExtension() {
        RUNNING_INSTANCE = this;

        enableButton.setDisable(true);
        delayTextField.setText("300");
        botNameTextField.setText("Thauan");
        botImageTextField.setText("https://xeol.online/images/logo_xeol_1.gif");
        extensionName.setText("Discord Room Informer v" + this.getInfoAnnotations().Version());

        timerSendPayload = new Timer(10000, e -> {
            if(enabled) {
                new Thread(this::sendPayload).start();
                startTime = System.currentTimeMillis();
            }
        });

        updateTimer = new Timer(1, e -> {
            long now = System.currentTimeMillis();
            long duration = timerSendPayload.getDelay();
            long clockTime = now - startTime;
            String pattern;
            if(duration >= 3600000) {
                pattern = "kk:mm:ss";
            }else {
                pattern = "mm:ss";
            }
            SimpleDateFormat df = new SimpleDateFormat(pattern);
            if (clockTime >= duration) {
                clockTime = duration;
            }
            long finalClockTime = clockTime;
            if(df.format(duration - finalClockTime).equals("00")) {
                Platform.runLater(() -> webhookTimerLabel.setText("Sending..."));
            }else {
                Platform.runLater(() -> webhookTimerLabel.setText("Sending in: " + df.format(duration - finalClockTime) + "s"));
            }
        });

        onConnect((host, port, APIVersion, versionClient, client) -> {
            this.host = host.substring(5, 7);
        });

        intercept(HMessage.Direction.TOCLIENT, "UserObject", hMessage -> {
            habboId = hMessage.getPacket().readInteger();
            habboName = hMessage.getPacket().readString();
        });

        intercept(HMessage.Direction.TOCLIENT, "UserRemove", hMessage -> {
            new Thread(() -> {
                try {
                    waitAFckingSec(100);
                    HPacket hPacket = hMessage.getPacket();
                    int leaverUserIndex = Integer.parseInt(hPacket.readString());
                    Player playerToRemove = null;
                    for(Player user : playerList) {
                        if(user.getIndex() == leaverUserIndex) {
                            playerToRemove = user;
                            Platform.runLater(() -> playerListView.getItems().remove(user.getName()));
                        }
                    }
                    if(playerToRemove != null)
                        playerList.remove(playerToRemove);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        });

        intercept(HMessage.Direction.TOCLIENT, "Users", hMessage -> {
            try {
                HPacket hPacket = hMessage.getPacket();
                HEntity[] roomUsersList = HEntity.parse(hPacket);
                for (HEntity hEntity : roomUsersList) {
                    String userName = hEntity.getName();
                    if(hEntity.getId() == habboId){
                        habboIndex = hEntity.getIndex();
                    }
                    if(!Objects.equals(hEntity.getName(), habboName) && hEntity.getEntityType() == HEntityType.HABBO) {
                        playerList.add(new Player(hEntity.getId(), hEntity.getIndex(), hEntity.getName()));
                        Platform.runLater(() -> {
                            playerListView.getItems().add(userName);
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        intercept(HMessage.Direction.TOCLIENT, "GetGuestRoomResult", hMessage -> {
            HPacket hPacket = hMessage.getPacket();
            hPacket.readBoolean();
            roomId = hPacket.readInteger();
            roomName = hPacket.readString();
            Platform.runLater(() -> {
                roomInfoLabel.setText("Name: " + roomName + "\nID: " + roomId + "\nUser Amount: " + playerList.size());
            });
        });

        intercept(HMessage.Direction.TOSERVER, "OpenFlatConnection", hMessage -> {
            HPacket hPacket = hMessage.getPacket();
            roomId = hPacket.readInteger();
            Platform.runLater(() -> playerListView.getItems().clear());
            playerList.clear();
            enabled = false;
            timerSendPayload.stop();
            Platform.runLater(() -> {
                delayTextField.setDisable(false);
                webhookTextField.setDisable(false);
                webhookTimerLabel.setVisible(false);
                enableButton.setText("Enable");
                labelInfo.setText("The webhook was disabled, because you changed room.");
                labelInfo.setTextFill(Color.RED);
            });
        });

    }
    public void sendPayload() {
        JSONObject discordPayload = new JSONObject();
        discordPayload.put("username", botNameTextField.getText());
        discordPayload.put("avatar_url", botImageTextField.getText());
        discordPayload.put("content", "");

        JSONArray embedsArray = new JSONArray();
        JSONObject embedObject = new JSONObject();

        JSONObject embedAuthorObject = new JSONObject();
        embedAuthorObject.put("name", "Room Information");
        embedAuthorObject.put("url", "https://github.com/thauanvargas/discord-room-info");
        embedAuthorObject.put("icon_url", "https://i.imgur.com/RpXxHEi.png");

        embedObject.put("author", embedAuthorObject);
        embedObject.put("title", roomName);
        embedObject.put("url", "https://www.habbo" + codeToDomainMap.get(host) + "/room/" + roomId);
        if(playerList.size() >= 1) {
            embedObject.put("description", "\nRoom ID: " + roomId + "\nThere's currently " + playerList.size() + " users at the room!\n### Users:");
        }else {
            embedObject.put("description", "\nRoom ID: " + roomId + "\nThere's no users in the room right now!");
        }
        embedObject.put("color", 15258703);

        JSONArray embedsFieldsArray = new JSONArray();

        JSONObject embedFieldsUsersObject = new JSONObject();
        StringBuilder userObjectValue = new StringBuilder();

        if(playerList.size() >= 1) {


            int j = 1;
            int i = 0;
            for(Player user : playerList) {
                String userName = user.getName();
                i++;
                userObjectValue.append("- ").append(userName).append("\n");

                if(j % 10 == 0) {
                    embedFieldsUsersObject.put("name", "");
                    embedFieldsUsersObject.put("value", userObjectValue);
                    embedFieldsUsersObject.put("inline", true);
                    embedsFieldsArray.put(embedFieldsUsersObject);
                    embedFieldsUsersObject = new JSONObject();
                    userObjectValue = new StringBuilder();
                }

                j++;
            }

            if(playerList.size() % 10 != 0 || playerList.size() < 10) {
                embedFieldsUsersObject.put("name", "");
                embedFieldsUsersObject.put("value", userObjectValue);
                embedsFieldsArray.put(embedFieldsUsersObject);
            }


        }

        JSONObject embedFieldsFooterObject = new JSONObject();

        embedFieldsFooterObject.put("name", "Click here to join the Room!");
        embedFieldsFooterObject.put("value", "https://www.habbo" + codeToDomainMap.get(host) + "/room/" + roomId);

        embedsFieldsArray.put(embedFieldsFooterObject);

        embedObject.put("fields", embedsFieldsArray);

        embedsArray.put(embedObject);
        discordPayload.put("embeds", embedsArray);

        String content = discordPayload.toString();

        sendJsonRequest(webhookTextField.getText(), content);
    }

    public void sendJsonRequest(String requestUrl, String content) {
        try {
            URL url = new URL (requestUrl);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            con.addRequestProperty("User-Agent", "Mozilla/5.0 (compatible; MSIE 8.0; Windows NT 6.1; Trident/4.0)");
            con.setDoOutput(true);
            try(OutputStream os = con.getOutputStream()) {
                byte[] input = content.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            try(BufferedReader br = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }
        }catch (IOException e){
            enableButton.setDisable(true);
            webhookTimerLabel.setVisible(false);
            enabled = false;
            System.out.println(e.getMessage());
            Platform.runLater(() -> {
                labelInfo.setText("There was a error, please check the webhook");
                labelInfo.setTextFill(Color.RED);
            });
        }
    }

    public void testWebHook(ActionEvent actionEvent) {

        if(botNameTextField.getText().isEmpty() || botImageTextField.getText().isEmpty()
                || delayTextField.getText().isEmpty() || webhookTextField.getText().isEmpty()) {
            Platform.runLater(() -> {
                labelInfo.setText("Fill all fields please.");
                labelInfo.setTextFill(Color.RED);
            });

            return;
        }

        JSONObject discordTestPayload = new JSONObject();
        discordTestPayload.put("username", "Habbo Room Informer");
        discordTestPayload.put("avatar_url", "https://xeol.online/images/logo_xeol_1.gif");
        discordTestPayload.put("content", "Hey your webhook for Discord Room Informer Extension is working!\nYour dev: thauanvargas");

        String content = discordTestPayload.toString();

        try {
            sendJsonRequest(webhookTextField.getText(), content);
            enableButton.setDisable(false);
            Platform.runLater(() -> {
                labelInfo.setText("Great, it seems it's working, now you can click the enable button!.");
                labelInfo.setTextFill(Color.GREEN);
            });

        }catch (Exception e) {
            enableButton.setDisable(true);
            enabled = false;
        }

    }

    public void waitAFckingSec(int millisecActually){
        try {
            Thread.sleep(millisecActually);
        } catch (InterruptedException ignored) { }
    }

    public void enableButtonClick(ActionEvent actionEvent) {
        enabled = !enabled;

        if(enabled) {
            startTime = System.currentTimeMillis();
            updateTimer.start();
            timerSendPayload.setDelay(Integer.parseInt(delayTextField.getText()) * 1000);
            timerSendPayload.start();
            Platform.runLater(() -> {
                delayTextField.setDisable(true);
                webhookTextField.setDisable(true);
                webhookTimerLabel.setVisible(true);
                enableButton.setText("Disable");
                labelInfo.setText("The webhook was enabled, you should get info shortly.");
                labelInfo.setTextFill(Color.GREEN);
            });
        }else {
            startTime = System.currentTimeMillis();
            updateTimer.stop();
            timerSendPayload.stop();
            Platform.runLater(() -> {
                delayTextField.setDisable(false);
                webhookTextField.setDisable(false);
                webhookTimerLabel.setVisible(false);
                enableButton.setText("Enable");
                labelInfo.setText("The webhook was disabled.");
                labelInfo.setTextFill(Color.RED);
            });
        }

    }
}
