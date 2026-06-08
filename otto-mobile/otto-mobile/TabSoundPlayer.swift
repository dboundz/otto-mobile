import AVFoundation
import Foundation
import os

@MainActor
final class TabSoundPlayer {
    static let shared = TabSoundPlayer()

    private var tabChangePlayer: AVAudioPlayer?
    private var userSharingPlayer: AVAudioPlayer?
    private var levelUpPlayer: AVAudioPlayer?
    private var startDrivePlayer: AVAudioPlayer?
    private var checkpointCompletePlayer: AVAudioPlayer?
    private var routeFinishedPlayer: AVAudioPlayer?

    private init() {
        Self.configureAudioSession()
        tabChangePlayer = Self.loadPlayer(resource: "tab-change", fileExtension: "wav")
        userSharingPlayer = Self.loadPlayer(resource: "user_sharing", fileExtension: "wav")
        levelUpPlayer = Self.loadPlayer(resource: "level_up", fileExtension: "mp3")
        startDrivePlayer = Self.loadPlayer(resource: "start-drive", fileExtension: "mp3")
        checkpointCompletePlayer = Self.loadPlayer(resource: "checkpoint-complete", fileExtension: "mp3")
        routeFinishedPlayer = Self.loadPlayer(resource: "route-finished", fileExtension: "mp3")
    }

    func play() {
        play(player: tabChangePlayer)
    }

    func playUserSharing() {
        play(player: userSharingPlayer)
    }

    func playLevelUp() {
        play(player: levelUpPlayer)
    }

    func playStartDrive() {
        play(player: startDrivePlayer)
    }

    func playCheckpointComplete() {
        play(player: checkpointCompletePlayer)
    }

    func playRouteFinished() {
        play(player: routeFinishedPlayer)
    }

    private func play(player: AVAudioPlayer?) {
        guard let player else { return }
        if player.isPlaying {
            player.currentTime = 0
        }
        player.play()
    }

    private static func loadPlayer(resource: String, fileExtension ext: String) -> AVAudioPlayer? {
        let url = Bundle.main.url(forResource: resource, withExtension: ext, subdirectory: "Resources/Sounds")
            ?? Bundle.main.url(forResource: resource, withExtension: ext)
        guard let url else {
            OttoLog.ui.error("\(resource).\(ext) missing from app bundle")
            return nil
        }

        do {
            let player = try AVAudioPlayer(contentsOf: url)
            player.prepareToPlay()
            return player
        } catch {
            OttoLog.ui.error("Failed to load \(resource).\(ext): \(String(describing: error))")
            return nil
        }
    }

    private static func configureAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, options: [.mixWithOthers])
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            OttoLog.ui.error("Failed to configure audio session: \(String(describing: error))")
        }
    }
}
