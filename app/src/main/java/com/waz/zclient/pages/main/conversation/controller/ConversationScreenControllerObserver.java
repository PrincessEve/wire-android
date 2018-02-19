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
import com.waz.model.ConvId;
import com.waz.model.IntegrationId;
import com.waz.model.ProviderId;
import com.waz.model.UserId;

public interface ConversationScreenControllerObserver {

    void onShowParticipants(View anchorView, boolean isSingleConversation, boolean isMemberOfConversation, boolean showDeviceTabIfSingle);

    void onHideParticipants(boolean backOrButtonPressed, boolean hideByConversationChange, boolean isSingleConversation);

    void onShowEditConversationName(boolean show);

    void onHeaderViewMeasured(int participantHeaderHeight);

    void onShowUser(UserId userId);

    void onHideUser();

    void onAddPeopleToConversation();

    void onShowConversationMenu(boolean inConvList, ConvId convId);

    void onHideOtrClient();

    void onShowLikesList(Message message);

    void onShowIntegrationDetails(ProviderId providerId, IntegrationId integrationId);
}
