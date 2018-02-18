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
package com.waz.zclient.participants.fragments

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.TextView
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.api.Verification
import com.waz.service.NetworkModeService
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.common.controllers.{ThemeController, UserAccountsController}
import com.waz.zclient.common.views.{ChatheadView, UserDetailsView}
import com.waz.zclient.controllers.navigation.{INavigationController, NavigationController}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.participants.ProfileAnimation
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.ui.animation.fragment.FadeAnimation
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.views.e2ee.ShieldView
import com.waz.zclient.{FragmentHelper, R}

import scala.concurrent.Future

class SingleParticipantFragment extends FragmentHelper {

  implicit def cxt: Context = getActivity

  lazy val themeController        = inject[ThemeController]
  lazy val participantsController = inject[ParticipantsController]
  lazy val usersController        = inject[UsersController]
  lazy val userAccountsController = inject[UserAccountsController]
  lazy val convController         = inject[ConversationController]
  lazy val networkService         = inject[NetworkModeService]

  lazy val screenController = inject[IConversationScreenController]
  lazy val navController    = inject[INavigationController]

  lazy val selUserId = participantsController.otherParticipant.collect { case Some(u) => u }

  lazy val selUser   = selUserId.flatMap(usersController.user)
  lazy val shieldView = returning(view[ShieldView](R.id.verified_shield)) { vh =>
    selUser.map(_.verified == Verification.VERIFIED).onUi(vis => vh.foreach(_.setVisible(vis)))
  }

  lazy val header = returning(view[TextView](R.id.toolbar__title)) { vh =>
    selUser.map(_.name).onUi(n => vh.foreach(_.setText(n)))
  }

  lazy val userDetails = returning(view[UserDetailsView](R.id.user_details)) { vh =>
    selUserId(u => vh.foreach(_.setUserId(u)))
  }

  lazy val chathead = returning(view[ChatheadView](R.id.chathead)) { vh =>
    selUserId(u => vh.foreach(_.setUserId(u)))
  }

  private var goToConversationWithUser = false


  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) =
    inflater.inflate(R.layout.fragment_participants_single, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    returning(findById[Toolbar](R.id.toolbar)) { t =>
      t.setNavigationIcon(if (themeController.isDarkTheme) R.drawable.action_back_light else R.drawable.action_back_dark)
      t.setNavigationOnClickListener(new View.OnClickListener() {
        override def onClick(v: View) =
          screenController.hideUser()
      })
    }

    getChildFragmentManager.beginTransaction
      .add(
        R.id.tab__container,
        TabbedParticipantBodyFragment.newInstance(TabbedParticipantBodyFragment.USER_PAGE),
        TabbedParticipantBodyFragment.TAG)
      .commit

    // Posting so that we can get height after onMeasure has been called
    // TODO seems dodgy...
    Future {
      val height = findById[View](R.id.header_container).getHeight
      findById[View](R.id.tab__container).setPadding(0, height, 0, 0)
    } (Threading.Ui)

    returning(findById[View](R.id.background_container)) { bc =>
      if (navController.getPagerPosition == NavigationController.FIRST_PAGE ||
          screenController.getPopoverLaunchMode == DialogLaunchMode.AVATAR ||
          screenController.getPopoverLaunchMode == DialogLaunchMode.COMMON_USER)
        bc.setClickable(true)
      else bc.setBackgroundColor(Color.TRANSPARENT)
    }

    shieldView
    header
    userDetails
    chathead
  }

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int) = {
    if ((screenController.getPopoverLaunchMode != DialogLaunchMode.AVATAR) &&
      (screenController.getPopoverLaunchMode != DialogLaunchMode.COMMON_USER)) {
      val centerX = getOrientationIndependentDisplayWidth / 2
      val centerY = getOrientationIndependentDisplayHeight / 2

      // Fade out animation when starting conversation directly with this user
      if (goToConversationWithUser && !enter) {
        goToConversationWithUser = false
        new FadeAnimation(getInt(R.integer.framework_animation_duration_medium), 1, 0)
      } else {
        val duration = getInt(if (enter) R.integer.open_profile__animation_duration else R.integer.close_profile__animation_duration)
        val delay = if (enter) getInt(R.integer.open_profile__delay) else 0
        new ProfileAnimation(enter, duration, delay, centerX, centerY)
      }
    } else super.onCreateAnimation(transit, enter, nextAnim)
  }
}

object SingleParticipantFragment {

  val Tag = implicitLogTag

  def newInstance(): SingleParticipantFragment =
    new SingleParticipantFragment
}
