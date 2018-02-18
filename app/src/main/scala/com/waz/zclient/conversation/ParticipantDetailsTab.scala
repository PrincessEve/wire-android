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
package com.waz.zclient.conversation

import android.content.Context
import android.widget.{ImageView, LinearLayout}
import com.waz.ZLog.ImplicitTag._
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.common.views.ImageAssetDrawable
import com.waz.zclient.common.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.common.views.ImageController.{ImageSource, WireImage}
import com.waz.zclient.messages.UsersController
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, UiStorage}
import com.waz.zclient.views.ShowAvailabilityView
import com.waz.zclient.views.menus.{FooterMenu, FooterMenuCallback}
import com.waz.zclient.{R, ViewHelper}

class ParticipantDetailsTab(val context: Context, callback: FooterMenuCallback) extends LinearLayout(context, null, 0) with ViewHelper {

  inflate(R.layout.single_participant_tab_details)
  setOrientation(LinearLayout.VERTICAL)

  private implicit val uiStorage     = inject[UiStorage]
  private val zms                    = inject[Signal[ZMessaging]]
  private val usersController        = inject[UsersController]
  private val participantsController = inject[ParticipantsController]
  private val userAccountsController = inject[UserAccountsController]

  private val imageView = findById[ImageView](R.id.chathead)

  private val footerMenu = returning(findById[FooterMenu](R.id.fm__footer)) {
    _.setCallback(callback)
  }

  private lazy val guestIndicationText = findById[TypefaceTextView](R.id.participant_guest_indicator)
  private lazy val userAvailability    = findById[ShowAvailabilityView](R.id.participant_availability)

  private val otherUser = for {
    z         <- zms
    Some(uId) <- participantsController.otherParticipant
    user      <- z.users.userSignal(uId)
  } yield user

  private val picture: Signal[ImageSource] =
    otherUser.map(_.picture).collect { case Some(pic) => WireImage(pic) }

  private val otherUserIsGuest = for {
    z       <- zms
    user    <- otherUser
    isGuest <- if (user.isWireBot) Signal.const(false) else z.teams.isGuest(user.id)
  } yield isGuest

  otherUserIsGuest.onUi {
    guestIndicationText.setVisible
  }

  otherUserIsGuest.map {
    case true  => getString(R.string.participant_tab_guest_indicator_label)
    case false => ""
  }.onUi {
    guestIndicationText.setText
  }

  imageView.setImageDrawable(new ImageAssetDrawable(picture, scaleType = ScaleType.CenterInside, request = RequestBuilder.Round))

  usersController.availabilityVisible.onUi { userAvailability.setVisible }

  (for {
    Some(uId) <- participantsController.otherParticipant
    av        <- usersController.availability(uId)
  } yield av).onUi {
    userAvailability.set
  }

  participantsController.isGroupOrBot.map {
    case false if userAccountsController.hasCreateConversationPermission => R.string.glyph__add_people
    case _                                                               => R.string.glyph__conversation
  }.onUi { id =>
    footerMenu.setLeftActionText(getString(id))
  }

  participantsController.isGroupOrBot.map {
    case false if userAccountsController.hasCreateConversationPermission => R.string.conversation__action__create_group
    case _                                                               => R.string.empty_string
  }.onUi { id =>
    footerMenu.setLeftActionLabelText(getString(id))
  }

  (for {
    convId     <- participantsController.conv.map(_.id)
    groupOrBot <- participantsController.isGroupOrBot
  } yield (groupOrBot, convId)).map {
    case (false, _)     if userAccountsController.hasCreateConversationPermission               => R.string.glyph__more
    case (true, convId) if userAccountsController.hasRemoveConversationMemberPermission(convId) => R.string.glyph__minus
    case _ => R.string.empty_string
  }.onUi { id =>
    footerMenu.setRightActionText(getString(id))
  }

}


