package org.alexdev.http.controllers.housekeeping;

import org.alexdev.duckhttpd.server.connection.WebConnection;
import org.alexdev.duckhttpd.template.Template;
import org.alexdev.havana.dao.mysql.PlayerDao;
import org.alexdev.havana.game.player.PlayerDetails;
import org.alexdev.havana.game.player.PlayerRank;
import org.alexdev.havana.server.rcon.messages.RconHeader;
import org.alexdev.havana.util.config.GameConfiguration;
import org.alexdev.http.Routes;
import org.alexdev.http.dao.housekeeping.HousekeepingLogsDao;
import org.alexdev.http.dao.housekeeping.HousekeepingPlayerDao;
import org.alexdev.http.game.housekeeping.HousekeepingManager;
import org.alexdev.http.util.RconUtil;
import org.alexdev.http.util.SessionUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HousekeepingRanksController {
    public static void giveRank (WebConnection client) {
        if (!client.session().getBoolean(SessionUtil.LOGGED_IN_HOUSKEEPING)) {
            client.redirect("/" + Routes.HOUSEKEEPING_DEFAULT_PATH);
            return;
        }

        Template tpl = client.template("housekeeping/admin_tools/give_rank");
        tpl.set("housekeepingManager", HousekeepingManager.getInstance());

        PlayerDetails playerDetails = (PlayerDetails) tpl.get("playerDetails");

        if (!HousekeepingManager.getInstance().hasPermission(playerDetails.getRank(), "user/ranks") && playerDetails.getId() != 1) {
            client.redirect("/" + Routes.HOUSEKEEPING_PATH + "/permissions");
            HousekeepingLogsDao.logHousekeepingAction("BAD_PERMISSIONS", playerDetails.getId(), playerDetails.getName(), "URL: " + client.request().uri(), client.getIpAddress());
            return;
        }

        String action = client.post().getString("action");

        if ("giveRank".equals(action)) {
            String user = client.post().getString("user");
            String rank = client.post().getString("rankId");
            boolean sendAlert = client.post().getBoolean("sendAlert");
            int rankId = 0;



            if (StringUtils.isNumeric(rank)) {
                rankId = Integer.parseInt(rank);
            }

            if (user.equalsIgnoreCase(String.valueOf(playerDetails.getName()))) {
                client.session().set("alertColour", "warning");
                client.session().set("alertMessage", "You can not change the rank yourself");

                client.redirect("/" + Routes.HOUSEKEEPING_PATH + "/admin_tools/give_rank");

                return;
            }

            if (user.length() > 0 && rankId > 0 && rankId < 8) {
                String rankName = String.valueOf(PlayerRank.getRankForId(rankId));
                String checkName = HousekeepingPlayerDao.CheckDBName(user);

                if (!checkName.equalsIgnoreCase(user)) {
                    client.session().set("alertColour", "danger");
                    client.session().set("alertMessage", "The user does not exist");
                } else {
                    var playerDetailsRank = PlayerDao.getDetails(user);

                    if (playerDetailsRank.getRank().getRankId() == rankId) {
                        client.session().set("alertColour", "warning");
                        client.session().set("alertMessage", "The user " + user + " already has the rank ID " + rankId + " (" + rankName + ")");
                    } else {
                        HousekeepingPlayerDao.setRank(user, rankId);
                        HousekeepingLogsDao.logHousekeepingAction("STAFF_ACTION", playerDetails.getId(), playerDetails.getName(), "Set the rank ID " + rankId + " (" + rankName + ") to user " + user + ". URL: " + client.request().uri(), client.getIpAddress());

                        client.session().set("alertColour", "success");
                        client.session().set("alertMessage", "Successfully set the rank ID " + rankId + " (" + rankName + ") to the user " + user);

                        client.redirect("/" + Routes.HOUSEKEEPING_PATH + "/admin_tools/give_rank");

                        if (sendAlert) {
                            String message = GameConfiguration.getInstance().getString("rcon.give.rank.message");
                            String finalMessage = StringUtils.replace(message, "%rank%", rankName);

                            RconUtil.sendCommand(RconHeader.MOD_ALERT_USER, new HashMap<>() {{
                                put("receiver", user);
                                put("message", finalMessage);

                            }});
                        }
                    }
                }
            } else {
                client.session().set("alertColour", "danger");
                client.session().set("alertMessage", "Please enter a valid username or rank");
            }
        }

        if ("staffVars".equals(action)) {
            String rankId = client.post().getString("rankIdVars");
            String rankName = client.post().getString("rankNameVars");
            String rankBadge = client.post().getString("rankBadgeVars");
            String rankDescription = client.post().getString("rankDescVars");

            int rankIdInt = 0;

            if (StringUtils.isNumeric(rankId) ) {
                rankIdInt = Integer.parseInt(rankId);
            }

            if (rankIdInt > 0 && rankIdInt < 8) {
                String rankNameVars = String.valueOf(PlayerRank.getRankForId(rankIdInt));

                if (rankName.length() > 0 && rankBadge.length() > 0 && rankDescription.length() > 0) {
                    HousekeepingPlayerDao.setRankTextVars(rankIdInt, rankName, rankBadge, rankDescription);
                    HousekeepingLogsDao.logHousekeepingAction("STAFF_ACTION", playerDetails.getId(), playerDetails.getName(), "Updated the variables for rank ID " + rankIdInt + ". URL: " + client.request().uri(), client.getIpAddress());


                    client.session().set("alertColour", "success");
                    client.session().set("alertMessage", "Successfully update the texts variables for rank ID " + rankIdInt + " (" + rankNameVars + ")");

                    client.redirect("/" + Routes.HOUSEKEEPING_PATH + "/admin_tools/give_rank");
                } else {
                    client.session().set("alertColour", "danger");
                    client.session().set("alertMessage", "Please fill all the texts variables for rank ID " + rankIdInt + " (" + rankNameVars + ")");
                }
            } else {
                client.session().set("alertColour", "danger");
                client.session().set("alertMessage", "Please enter a valid rank ID in the staff texts variables");
            }
        }

        List<Map<String, Object>> allStaffsNames = HousekeepingPlayerDao.getAllStaffsNames();
        List<Map<String, Object>> allStaffDetails = new ArrayList<>();

        for (Map<String, Object> staff : allStaffsNames) {
            int staffId = (int) staff.get("id");

            PlayerDetails playerDetailsStaff = PlayerDao.getDetails(staffId);

            Map<String, Object> staffDetails = new HashMap<>();
            staffDetails.put("id", playerDetailsStaff.getId());
            staffDetails.put("name", playerDetailsStaff.getName());
            staffDetails.put("rankId", playerDetailsStaff.getRank().getRankId());
            staffDetails.put("rankName", playerDetailsStaff.getRank().getName());

            allStaffDetails.add(staffDetails);
        }

        tpl.set("pageName", "Give rank tool");
        tpl.set("allRanks", HousekeepingPlayerDao.getAllRanks());
        tpl.set("staffDetailsList", allStaffDetails);
        tpl.render();

        client.session().delete("alertMessage");
    }
}