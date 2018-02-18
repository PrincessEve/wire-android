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
package com.waz.zclient.pages.main.connect;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import com.waz.api.IConversation;
import com.waz.api.UpdateListener;
import com.waz.api.User;
import com.waz.model.ConvId;
import com.waz.model.ConversationData;
import com.waz.model.UserId;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.conversation.ConversationController;
import com.waz.zclient.core.stores.connect.ConnectStoreObserver;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.participants.ProfileAnimation;
import com.waz.zclient.pages.main.participants.ProfileTabletAnimation;
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.utils.Callback;
import com.waz.zclient.utils.ContextUtils;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.images.ImageAssetImageView;
import com.waz.zclient.views.menus.FooterMenu;
import com.waz.zclient.views.menus.FooterMenuCallback;

public class PendingConnectRequestFragment extends BaseFragment<PendingConnectRequestFragment.Container> implements ConnectStoreObserver,
                                                                                                                    AccentColorObserver,
                                                                                                                    UpdateListener {

    public static final String TAG = PendingConnectRequestFragment.class.getName();
    public static final String ARGUMENT_USER_ID = "ARGUMENT_USER_ID";
    public static final String ARGUMENT_CONVERSATION_ID = "ARGUMENT_CONVERSATION_ID";
    public static final String ARGUMENT_LOAD_MODE = "ARGUMENT_LOAD_MODE";
    public static final String ARGUMENT_USER_REQUESTER = "ARGUMENT_USER_REQUESTER";
    public static final String STATE_IS_SHOWING_FOOTER_MENU = "STATE_IS_SHOWING_FOOTER_MENU";

    private String userId;
    private ConvId conversationId;
    private IConversation conversation;
    private ConnectRequestLoadMode loadMode;
    private IConnectStore.UserRequester userRequester;

    private boolean isShowingFooterMenu;

    private ZetaButton unblockButton;
    private FooterMenu footerMenu;
    private ImageAssetImageView imageAssetImageViewProfile;

    public static PendingConnectRequestFragment newInstance(String userId,
                                                            String conversationId,
                                                            ConnectRequestLoadMode loadMode,
                                                            IConnectStore.UserRequester userRequester) {
        PendingConnectRequestFragment newFragment = new PendingConnectRequestFragment();

        Bundle args = new Bundle();
        args.putString(ARGUMENT_USER_ID, userId);
        args.putString(ARGUMENT_CONVERSATION_ID, conversationId);
        args.putString(ARGUMENT_USER_REQUESTER, userRequester.toString());
        args.putString(ARGUMENT_LOAD_MODE, loadMode.toString());
        newFragment.setArguments(args);

        return newFragment;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Lifecycle
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        Animation animation = super.onCreateAnimation(transit, enter, nextAnim);

        if (getControllerFactory().getConversationScreenController().getPopoverLaunchMode() != DialogLaunchMode.AVATAR &&
            getControllerFactory().getConversationScreenController().getPopoverLaunchMode() != DialogLaunchMode.COMMON_USER) {
            // No animation when request is shown in conversation list
            IConnectStore.UserRequester userRequester = IConnectStore.UserRequester.valueOf(getArguments().getString(ARGUMENT_USER_REQUESTER));
            if (userRequester != IConnectStore.UserRequester.CONVERSATION) {
                int centerX = ContextUtils.getOrientationIndependentDisplayWidth(getActivity()) / 2;
                int centerY = ContextUtils.getOrientationIndependentDisplayHeight(getActivity()) / 2;
                int duration;
                int delay = 0;
                if (nextAnim != 0) {
                    if (enter) {
                        duration = getResources().getInteger(R.integer.open_profile__animation_duration);
                        delay = getResources().getInteger(R.integer.open_profile__delay);
                    } else {
                        duration = getResources().getInteger(R.integer.close_profile__animation_duration);
                    }
                    animation = new ProfileAnimation(enter, duration, delay, centerX, centerY);
                }
            }
        }
        return animation;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup viewContainer, Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            userId = savedInstanceState.getString(ARGUMENT_USER_ID);
            conversationId = new ConvId(savedInstanceState.getString(ARGUMENT_CONVERSATION_ID));
            loadMode = ConnectRequestLoadMode.valueOf(savedInstanceState.getString(ARGUMENT_LOAD_MODE));
            userRequester = IConnectStore.UserRequester.valueOf(savedInstanceState.getString(ARGUMENT_USER_REQUESTER));
            isShowingFooterMenu = savedInstanceState.getBoolean(STATE_IS_SHOWING_FOOTER_MENU);
        } else {
            userId = getArguments().getString(ARGUMENT_USER_ID);
            conversationId = new ConvId(getArguments().getString(ARGUMENT_CONVERSATION_ID));
            loadMode = ConnectRequestLoadMode.valueOf(getArguments().getString(ARGUMENT_LOAD_MODE));
            userRequester = IConnectStore.UserRequester.valueOf(getArguments().getString(ARGUMENT_USER_REQUESTER));
            isShowingFooterMenu = false;
        }

        View rootView = inflater.inflate(R.layout.fragment_connect_request_pending, viewContainer, false);

        unblockButton = ViewUtils.getView(rootView, R.id.zb__connect_request__unblock_button);
        footerMenu = ViewUtils.getView(rootView, R.id.fm__footer);
        imageAssetImageViewProfile = ViewUtils.getView(rootView, R.id.iaiv__pending_connect);
        imageAssetImageViewProfile.setDisplayType(ImageAssetImageView.DisplayType.CIRCLE);
        imageAssetImageViewProfile.setSaturation(0);

        View backgroundContainer = ViewUtils.getView(rootView, R.id.ll__pending_connect__background_container);
        if (getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.AVATAR ||
            getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.COMMON_USER) {
            backgroundContainer.setClickable(true);
        } else {
            backgroundContainer.setBackgroundColor(Color.TRANSPARENT);
        }

        // Hide views until connection status of user is determined
        footerMenu.setVisibility(View.GONE);
        unblockButton.setVisibility(View.GONE);

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        getStoreFactory().connectStore().addConnectRequestObserver(this);

        switch (loadMode) {
            case LOAD_BY_CONVERSATION_ID:
                inject(ConversationController.class).withConvLoaded(conversationId, new Callback<ConversationData>() {
                    @Override
                    public void callback(ConversationData conversationData) {
                        onConversationLoaded(conversationData);
                    }
                });
                break;
            case LOAD_BY_USER_ID:
                getStoreFactory().connectStore().loadUser(userId, userRequester);
                break;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(ARGUMENT_USER_ID, userId);
        outState.putString(ARGUMENT_CONVERSATION_ID, conversationId.str());
        if (loadMode != null) {
            outState.putString(ARGUMENT_LOAD_MODE, loadMode.toString());
        }
        if (userRequester != null) {
            outState.putString(ARGUMENT_USER_REQUESTER, userRequester.toString());
        }
        // Save if footer menu was visible -> used to toggle accept & footer menu in incoming connect request opened from group participants
        outState.putBoolean(STATE_IS_SHOWING_FOOTER_MENU, isShowingFooterMenu);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStop() {

        getStoreFactory().connectStore().removeConnectRequestObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);

        super.onStop();
    }

    @Override
    public void onDestroyView() {
        imageAssetImageViewProfile = null;
        unblockButton = null;
        footerMenu = null;
        if (conversation != null) {
            conversation.removeUpdateListener(this);
            conversation = null;
        }
        super.onDestroyView();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  UI
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    private void setFooterForOutgoingConnectRequest(final User user) {
        // Show footer
        footerMenu.setVisibility(View.VISIBLE);
        isShowingFooterMenu = true;
        footerMenu.setRightActionText("");

        footerMenu.setCallback(new FooterMenuCallback() {
            @Override
            public void onLeftActionClicked() {
                user.cancelConnection();
                getActivity().onBackPressed();
            }

            @Override
            public void onRightActionClicked() {
            }
        });
    }


    private void setFooterForIncomingConnectRequest(final User user) {
        if (userRequester != IConnectStore.UserRequester.PARTICIPANTS)  {
            return;
        }

        footerMenu.setVisibility(View.VISIBLE);
        footerMenu.setRightActionText(getString(R.string.glyph__minus));

        footerMenu.setCallback(new FooterMenuCallback() {
            @Override
            public void onLeftActionClicked() {
                IConversation conversation = user.acceptConnection();
                getContainer().onAcceptedConnectRequest(new ConvId(conversation.getId()));
            }

            @Override
            public void onRightActionClicked() {
                getContainer().showRemoveConfirmation(new UserId(user.getId()));
            }
        });

        footerMenu.setLeftActionText(getString(R.string.glyph__plus));
        footerMenu.setLeftActionLabelText(getString(R.string.send_connect_request__connect_button__text));
    }

    private void setFooterForIgnoredConnectRequest(final User user) {
        footerMenu.setVisibility(View.VISIBLE);
        footerMenu.setRightActionText("");
        footerMenu.setRightActionLabelText("");

        footerMenu.setCallback(new FooterMenuCallback() {
            @Override
            public void onLeftActionClicked() {
                IConversation conversation = user.acceptConnection();
                getContainer().onAcceptedConnectRequest(new ConvId(conversation.getId()));
            }

            @Override
            public void onRightActionClicked() {
            }
        });

        footerMenu.setLeftActionText(getString(R.string.glyph__plus));
        footerMenu.setLeftActionLabelText(getString(R.string.send_connect_request__connect_button__text));
    }

    private void onConversationLoaded(ConversationData conv) {

        switch (loadMode) {
            case LOAD_BY_CONVERSATION_ID:
                if (this.conversation != null) {
                    this.conversation.removeUpdateListener(this);
                }
                this.conversation = inject(ConversationController.class).iConv(conv.id());
                if (conversation != null) {
                    conversation.addUpdateListener(this);
                }

                getStoreFactory().connectStore().loadUser(ConversationController.getOtherParticipantForOneToOneConv(conv).str(), userRequester);

                break;
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  ConnectStoreObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnectUserUpdated(final User user, IConnectStore.UserRequester userRequester) {
        if (this.userRequester != userRequester ||
            user == null) {
            return;
        }

        switch (loadMode) {
            case LOAD_BY_USER_ID:
                inject(ConversationController.class).withConvLoaded(new ConvId(user.getConversation().getId()), new Callback<ConversationData>() {
                    @Override
                    public void callback(ConversationData conversationData) {
                        onConversationLoaded(conversationData);
                    }
                });
                break;
        }

        imageAssetImageViewProfile.connectImageAsset(user.getPicture());

        switch (user.getConnectionStatus()) {
            case PENDING_FROM_OTHER:
                setFooterForIncomingConnectRequest(user);
                break;
            case IGNORED:
                setFooterForIgnoredConnectRequest(user);
                break;
            case PENDING_FROM_USER:
                setFooterForOutgoingConnectRequest(user);
                break;
        }
    }

    @Override
    public void onInviteRequestSent(IConversation conversation) {

    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  AccentColorControllerObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        unblockButton.setIsFilled(false);
        unblockButton.setAccentColor(color);
    }
    
    @Override
    public void updated() {
        if (conversation != null && conversation.getType() == IConversation.Type.ONE_TO_ONE) {
            getContainer().onConversationUpdated(new ConvId(conversation.getId()));
        }
    }

    public interface Container extends UserProfileContainer {
        void onAcceptedConnectRequest(ConvId conversation);

        void onConversationUpdated(ConvId conversation);
    }
}
