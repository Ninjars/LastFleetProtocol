package jez.lastfleetprotocol.prototype.ui.resources

import lastfleetprotocol.components.design.generated.resources.Res
import lastfleetprotocol.components.design.generated.resources.builder_acceleration
import lastfleetprotocol.components.design.generated.resources.builder_angular
import lastfleetprotocol.components.design.generated.resources.builder_design_name
import lastfleetprotocol.components.design.generated.resources.builder_forward
import lastfleetprotocol.components.design.generated.resources.builder_hull_pieces
import lastfleetprotocol.components.design.generated.resources.builder_lateral
import lastfleetprotocol.components.design.generated.resources.builder_load_design
import lastfleetprotocol.components.design.generated.resources.builder_mass
import lastfleetprotocol.components.design.generated.resources.builder_no_saved_designs
import lastfleetprotocol.components.design.generated.resources.builder_reverse
import lastfleetprotocol.components.design.generated.resources.builder_stats
import lastfleetprotocol.components.design.generated.resources.builder_systems
import lastfleetprotocol.components.design.generated.resources.builder_thrust
import lastfleetprotocol.components.design.generated.resources.builder_turrets
import lastfleetprotocol.components.design.generated.resources.bullet_laser_green_10
import lastfleetprotocol.components.design.generated.resources.button_cancel
import lastfleetprotocol.components.design.generated.resources.button_continue
import lastfleetprotocol.components.design.generated.resources.button_exit
import lastfleetprotocol.components.design.generated.resources.button_new_game
import lastfleetprotocol.components.design.generated.resources.button_restart
import lastfleetprotocol.components.design.generated.resources.button_resume
import lastfleetprotocol.components.design.generated.resources.button_settings
import lastfleetprotocol.components.design.generated.resources.button_ship_builder
import lastfleetprotocol.components.design.generated.resources.desc_toggle_music
import lastfleetprotocol.components.design.generated.resources.desc_toggle_sound_effects
import lastfleetprotocol.components.design.generated.resources.game_defeat
import lastfleetprotocol.components.design.generated.resources.game_paused
import lastfleetprotocol.components.design.generated.resources.game_victory
import lastfleetprotocol.components.design.generated.resources.ic_back
import lastfleetprotocol.components.design.generated.resources.ic_exit
import lastfleetprotocol.components.design.generated.resources.ic_menu
import lastfleetprotocol.components.design.generated.resources.ic_music_off
import lastfleetprotocol.components.design.generated.resources.ic_music_on
import lastfleetprotocol.components.design.generated.resources.ic_sound_effects_off
import lastfleetprotocol.components.design.generated.resources.ic_sound_effects_on
import lastfleetprotocol.components.design.generated.resources.megrim_regular
import lastfleetprotocol.components.design.generated.resources.sairacondensed_black
import lastfleetprotocol.components.design.generated.resources.sairacondensed_bold
import lastfleetprotocol.components.design.generated.resources.sairacondensed_light
import lastfleetprotocol.components.design.generated.resources.sairacondensed_medium
import lastfleetprotocol.components.design.generated.resources.sairacondensed_regular
import lastfleetprotocol.components.design.generated.resources.sairacondensed_semibold
import lastfleetprotocol.components.design.generated.resources.sairacondensed_thin
import lastfleetprotocol.components.design.generated.resources.ship_enemy_1
import lastfleetprotocol.components.design.generated.resources.ship_player_1
import lastfleetprotocol.components.design.generated.resources.sprite_alien_ship
import lastfleetprotocol.components.design.generated.resources.sprite_power_up
import lastfleetprotocol.components.design.generated.resources.sprite_shield
import lastfleetprotocol.components.design.generated.resources.sprite_ship
import lastfleetprotocol.components.design.generated.resources.title_splash
import lastfleetprotocol.components.design.generated.resources.turret_simple_1
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.StringResource

object LFRes {
    object Drawable {
        val ic_exit: DrawableResource = Res.drawable.ic_exit
        val ic_back: DrawableResource = Res.drawable.ic_back
        val ic_menu: DrawableResource = Res.drawable.ic_menu
        val ic_music_off: DrawableResource = Res.drawable.ic_music_off
        val ic_music_on: DrawableResource = Res.drawable.ic_music_on
        val ic_sound_effects_off: DrawableResource = Res.drawable.ic_sound_effects_off
        val ic_sound_effects_on: DrawableResource = Res.drawable.ic_sound_effects_on
        val sprite_ship: DrawableResource = Res.drawable.sprite_ship
        val sprite_alien_ship: DrawableResource = Res.drawable.sprite_alien_ship
        val sprite_power_up: DrawableResource = Res.drawable.sprite_power_up
        val sprite_shield: DrawableResource = Res.drawable.sprite_shield
        val ship_player_1: DrawableResource = Res.drawable.ship_player_1
        val ship_enemy_1: DrawableResource = Res.drawable.ship_enemy_1
        val turret_simple_1: DrawableResource = Res.drawable.turret_simple_1
        val bullet_laser_green_10: DrawableResource = Res.drawable.bullet_laser_green_10
    }

    object String {
        val title_splash: StringResource = Res.string.title_splash
        val button_new_game: StringResource = Res.string.button_new_game
        val button_continue: StringResource = Res.string.button_continue
        val button_settings: StringResource = Res.string.button_settings
        val button_ship_builder: StringResource = Res.string.button_ship_builder
        val desc_toggle_music: StringResource = Res.string.desc_toggle_music
        val desc_toggle_sound_effects: StringResource = Res.string.desc_toggle_sound_effects

        val game_paused: StringResource = Res.string.game_paused
        val game_victory: StringResource = Res.string.game_victory
        val game_defeat: StringResource = Res.string.game_defeat
        val button_resume: StringResource = Res.string.button_resume
        val button_restart: StringResource = Res.string.button_restart
        val button_exit: StringResource = Res.string.button_exit
        val button_cancel: StringResource = Res.string.button_cancel

        val builder_stats: StringResource = Res.string.builder_stats
        val builder_design_name: StringResource = Res.string.builder_design_name
        val builder_thrust: StringResource = Res.string.builder_thrust
        val builder_acceleration: StringResource = Res.string.builder_acceleration
        val builder_mass: StringResource = Res.string.builder_mass
        val builder_forward: StringResource = Res.string.builder_forward
        val builder_lateral: StringResource = Res.string.builder_lateral
        val builder_reverse: StringResource = Res.string.builder_reverse
        val builder_angular: StringResource = Res.string.builder_angular
        val builder_load_design: StringResource = Res.string.builder_load_design
        val builder_no_saved_designs: StringResource = Res.string.builder_no_saved_designs
        val builder_hull_pieces: StringResource = Res.string.builder_hull_pieces
        val builder_systems: StringResource = Res.string.builder_systems
        val builder_turrets: StringResource = Res.string.builder_turrets
    }

    object Font {
        val megrim_regular: FontResource = Res.font.megrim_regular
        val sairacondensed_black: FontResource = Res.font.sairacondensed_black
        val sairacondensed_bold: FontResource = Res.font.sairacondensed_bold
        val sairacondensed_light: FontResource = Res.font.sairacondensed_light
        val sairacondensed_medium: FontResource = Res.font.sairacondensed_medium
        val sairacondensed_regular: FontResource = Res.font.sairacondensed_regular
        val sairacondensed_semibold: FontResource = Res.font.sairacondensed_semibold
        val sairacondensed_thin: FontResource = Res.font.sairacondensed_thin
    }
}
