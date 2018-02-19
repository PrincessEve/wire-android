/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.pages.main.conversation.controller;

import android.view.View;
import com.waz.api.Message;
import com.waz.api.User;
import com.waz.model.ConvId;
import com.waz.model.IntegrationId;
import com.waz.model.ProviderId;
import com.waz.model.UserId;
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode;

public interface IConversationScreenController {

    void addConversationControllerObservers(ConversationScreenControllerObserver conversationScreenControllerObserver);

    void removeConversationControllerObservers(ConversationScreenControllerObserver conversationScreenControllerObserver);

    void showParticipants(View anchorView, boolean showDeviceTabIfSingle);

    void hideParticipants(boolean backOrButtonPressed, boolean hideByConversationChange);

    void editConversationName(boolean b);

    void setShowDevicesTab(User user);

    boolean shouldShowDevicesTab();

    boolean isShowingParticipant();

    void resetToMessageStream();

    void setParticipantHeaderHeight(int participantHeaderHeight);

    void setSingleConversation(boolean isSingleConversation);

    void setMemberOfConversation(boolean isMemberOfConversation);

    void addPeopleToConversation();

    boolean showUser(UserId userId);

    void hideUser();

    boolean isShowingUser();

    void tearDown();

    void setPopoverLaunchedMode(DialogLaunchMode launchedMode);

    void showConversationMenu(boolean inConvList, ConvId convId);

    DialogLaunchMode getPopoverLaunchMode();

    void hideOtrClient();

    void showLikesList(Message message);

    void showIntegrationDetails(ProviderId providerId, IntegrationId integrationId);
}
