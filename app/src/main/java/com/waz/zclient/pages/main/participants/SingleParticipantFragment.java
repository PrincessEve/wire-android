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
package com.waz.zclient.pages.main.participants;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import com.waz.api.NetworkMode;
import com.waz.api.User;
import com.waz.model.ConvId;
import com.waz.model.UserId;
import com.waz.utils.wrappers.AndroidURIUtil;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.common.controllers.SoundController;
import com.waz.zclient.common.controllers.ThemeController;
import com.waz.zclient.common.views.UserDetailsView;
import com.waz.zclient.controllers.confirmation.ConfirmationCallback;
import com.waz.zclient.controllers.confirmation.ConfirmationRequest;
import com.waz.zclient.controllers.confirmation.IConfirmationController;
import com.waz.zclient.controllers.confirmation.TwoButtonConfirmationCallback;
import com.waz.zclient.controllers.navigation.NavigationController;
import com.waz.zclient.conversation.ConversationController;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.network.NetworkAction;
import com.waz.zclient.core.stores.singleparticipants.SingleParticipantStoreObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.connect.UserProfileContainer;
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode;
import com.waz.zclient.participants.fragments.TabbedParticipantBodyFragment;
import com.waz.zclient.ui.animation.fragment.FadeAnimation;
import com.waz.zclient.ui.theme.OptionsTheme;
import com.waz.zclient.utils.ContextUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.images.ImageAssetImageView;
import com.waz.zclient.views.menus.FooterMenu;
import com.waz.zclient.views.menus.FooterMenuCallback;
import timber.log.Timber;

public class SingleParticipantFragment extends BaseFragment<SingleParticipantFragment.Container> implements
                                                                                                 SingleParticipantStoreObserver,
                                                                                                 OnBackPressedListener,
                                                                                                 TabbedParticipantBodyFragment.Container,
                                                                                                 ParticipantBackbarFragment.Container {
    public static final String TAG = SingleParticipantFragment.class.getName();
    public static final String ARGUMENT_SHOWING_COMMON_USER = "ARGUMENT_SHOWING_COMMON_USER";
    private static final String SAVE_STATE_OTHER_USER_PROFILE_SCREEN_WAS_TRACKED = "SAVE_STATE_OTHER_USER_PROFILE_SCREEN_WAS_TRACKED";
    private static final String ARGUMENT_USER_REQUESTER = "ARGUMENT_USER_REQUESTER";

    private boolean goToConversationWithUser;
    private FooterMenu footerMenu;
    private boolean otherUserProfileScreenWasTracked;
    private ImageAssetImageView imageAssetImageViewProfile;

    public static SingleParticipantFragment newInstance(boolean showingCommonUser,
                                                        IConnectStore.UserRequester userRequester) {
        SingleParticipantFragment newFragment = new SingleParticipantFragment();

        Bundle args = new Bundle();
        args.putBoolean(ARGUMENT_SHOWING_COMMON_USER, showingCommonUser);
        args.putSerializable(ARGUMENT_USER_REQUESTER, userRequester);
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
            int centerX = ContextUtils.getOrientationIndependentDisplayWidth(getActivity()) / 2;
            int centerY = ContextUtils.getOrientationIndependentDisplayHeight(getActivity()) / 2;
            int duration;
            int delay = 0;

            // Fade out animation when starting conversation directly with this user
            if (goToConversationWithUser && !enter) {
                goToConversationWithUser = false;
                duration = getResources().getInteger(R.integer.framework_animation_duration_medium);
                animation = new FadeAnimation(duration, 1, 0);
            } else {
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
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_participants_single, viewGroup, false);

        Bundle args = getArguments();
        IConnectStore.UserRequester requester = null;
        if (args != null) {
            requester = (IConnectStore.UserRequester) args.getSerializable(ARGUMENT_USER_REQUESTER);
        }

        footerMenu = ViewUtils.getView(view, R.id.upm__footer);
        imageAssetImageViewProfile = ViewUtils.getView(view, R.id.iaiv__single_participant);
        imageAssetImageViewProfile.setDisplayType(ImageAssetImageView.DisplayType.CIRCLE);

        if (requester == IConnectStore.UserRequester.PARTICIPANTS) {
            footerMenu.setVisibility(View.GONE);
            imageAssetImageViewProfile.setVisibility(View.GONE);
            getChildFragmentManager().beginTransaction()
                                     .add(R.id.fl__participant__tab__container,
                                          TabbedParticipantBodyFragment.newInstance(TabbedParticipantBodyFragment.USER_PAGE()),
                                          TabbedParticipantBodyFragment.TAG())
                                     .commit();
        }

        View backgroundContainer = ViewUtils.getView(view, R.id.fl__send_connect_request__background_container);
        if (getControllerFactory().getNavigationController().getPagerPosition() == NavigationController.FIRST_PAGE ||
            (getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.AVATAR ||
             getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.COMMON_USER)) {
            backgroundContainer.setClickable(true);
        } else {
            backgroundContainer.setBackgroundColor(Color.TRANSPARENT);
        }

        // Hide footer until user is loaded
        footerMenu.setVisibility(View.GONE);

        if (savedInstanceState == null) {
            otherUserProfileScreenWasTracked = false;
        } else {
            otherUserProfileScreenWasTracked = savedInstanceState.getBoolean(SAVE_STATE_OTHER_USER_PROFILE_SCREEN_WAS_TRACKED);
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        getStoreFactory().singleParticipantStore().addSingleParticipantObserver(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(SAVE_STATE_OTHER_USER_PROFILE_SCREEN_WAS_TRACKED, otherUserProfileScreenWasTracked);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStop() {
        getStoreFactory().singleParticipantStore().removeSingleParticipantObserver(this);

        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (getContainer() != null &&
            !getControllerFactory().isTornDown()) {
            getControllerFactory().getSingleImageController().clearReferences();
        }

        imageAssetImageViewProfile = null;
        footerMenu = null;
        super.onDestroyView();
    }


    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  SingleParticipantStoreObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onUserUpdated(final User user) {
        if (user == null) {
            return;
        }

        Timber.i("onUserUpdated(%s)", user.getName());

        imageAssetImageViewProfile.connectImageAsset(user.getPicture());

        // Show footer if profile is not for self user
        if (!user.isMe()) {
            if (!otherUserProfileScreenWasTracked) {
                otherUserProfileScreenWasTracked = true;
            }
            final boolean showingCommonUser = getArguments().getBoolean(ARGUMENT_SHOWING_COMMON_USER);
            final boolean ignoreRightClick = getArguments().getSerializable(ARGUMENT_USER_REQUESTER) == IConnectStore.UserRequester.CALL;
            if (ignoreRightClick) {
                footerMenu.setRightActionText("");
            } else if (showingCommonUser) {
                footerMenu.setRightActionText(getResources().getString(R.string.glyph__block));
            }

            footerMenu.setCallback(new FooterMenuCallback() {
                @Override
                public void onLeftActionClicked() {
                    getControllerFactory().getConversationScreenController().hideParticipants(true, false);

                    // Go to conversation with this user
                    goToConversationWithUser = true;
                    getContainer().dismissUserProfile();
                    Timber.i("onUserUpdated %s", user.getConversation().getId());
                    inject(ConversationController.class).selectConv(new ConvId(user.getConversation().getId()), ConversationChangeRequester.START_CONVERSATION);
                }

                @Override
                public void onRightActionClicked() {
                    if (ignoreRightClick) {
                        return;
                    }
                    if (showingCommonUser) {
                        showBlockConfirmation(user);
                    } else {
                        getStoreFactory().networkStore().doIfHasInternetOrNotifyUser(new NetworkAction() {
                            @Override
                            public void execute(NetworkMode networkMode) {
                                getContainer().showRemoveConfirmation(new UserId(user.getId()));
                            }

                            @Override
                            public void onNoNetwork() {
                                ViewUtils.showAlertDialog(getActivity(),
                                                          R.string.alert_dialog__no_network__header,
                                                          R.string.remove_from_conversation__no_network__message,
                                                          R.string.alert_dialog__confirmation,
                                                          null, true);
                            }
                        });
                    }
                }
            });
            if (getArguments().getSerializable(ARGUMENT_USER_REQUESTER) != IConnectStore.UserRequester.PARTICIPANTS) {
                footerMenu.setVisibility(View.VISIBLE);
            }
        }
    }

    private void showBlockConfirmation(final User user) {
        ConfirmationCallback callback = new TwoButtonConfirmationCallback() {
            @Override
            public void positiveButtonClicked(boolean checkboxIsSelected) {
                getStoreFactory().connectStore().blockUser(user);
                // Dismiss common user profile
                getContainer().dismissUserProfile();
            }

            @Override
            public void negativeButtonClicked() {
            }

            @Override
            public void onHideAnimationEnd(boolean confirmed, boolean canceled, boolean checkboxIsSelected) {

            }
        };
        String header = getString(R.string.confirmation_menu__block_header);
        String text = getString(R.string.confirmation_menu__block_text_with_name, user.getDisplayName());
        String confirm = getString(R.string.confirmation_menu__confirm_block);
        String cancel = getString(R.string.confirmation_menu__cancel);
        OptionsTheme optionsTheme = ((BaseActivity) getActivity()).injectJava(ThemeController.class).getThemeDependentOptionsTheme();

        ConfirmationRequest request = new ConfirmationRequest.Builder()
            .withHeader(header)
            .withMessage(text)
            .withPositiveButton(confirm)
            .withNegativeButton(cancel)
            .withConfirmationCallback(callback)
            .withWireTheme(optionsTheme)
            .build();

        getControllerFactory().getConfirmationController().requestConfirmation(request, IConfirmationController.USER_PROFILE);

        SoundController ctrl = inject(SoundController.class);
        if (ctrl != null) {
            ctrl.playAlert();
        }
    }

    @Override
    public boolean onBackPressed() {
        Timber.i("PF onBackPressed");
        getFragmentManager().popBackStackImmediate(SingleParticipantFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        return true;
    }

    @Override
    public void showRemoveConfirmation(UserId userId) {
        getContainer().showRemoveConfirmation(userId);
    }

    public interface Container extends UserProfileContainer {
    }
}
