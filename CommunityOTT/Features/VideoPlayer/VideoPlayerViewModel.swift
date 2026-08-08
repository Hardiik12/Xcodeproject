//
//  VideoPlayerViewModel.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI
import AVKit
import Combine

public enum PlaybackState: Equatable, Sendable {
    case loading
    case ready
    case playing
    case paused
    case finished
    case failed(String)
}

@MainActor
public final class VideoPlayerViewModel: ObservableObject {
    @Published public private(set) var state: PlaybackState = .loading
    @Published public var currentTime: Double = 0.0
    @Published public var duration: Double = 0.0
    @Published public var isControlsVisible: Bool = true
    @Published public var selectedSubtitleTrack: SubtitleTrack?
    
    public let stream: MediaStream
    public private(set) var player: AVPlayer?
    
    private var timeObserverToken: Any?
    private var cancellables = Set<AnyCancellable>()
    private var controlsTimer: Timer?
    
    public init(stream: MediaStream) {
        self.stream = stream
    }
    
    public func setupPlayer() {
        state = .loading
        
        let url: URL?
        if stream.streamURL.hasPrefix("file://") || stream.streamURL.hasPrefix("http") {
            url = URL(string: stream.streamURL)
        } else if let bundleURL = Bundle.main.url(forResource: "sample_video", withExtension: "mp4") {
            url = bundleURL
        } else {
            // Check workspace resources path
            url = URL(fileURLWithPath: stream.streamURL)
        }
        
        guard let validURL = url else {
            state = .failed("Unable to locate media stream resource.")
            return
        }
        
        let asset = AVURLAsset(url: validURL)
        let playerItem = AVPlayerItem(asset: asset)
        
        let newPlayer = AVPlayer(playerItem: playerItem)
        self.player = newPlayer
        
        // Observe status
        playerItem.publisher(for: \.status)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in
                guard let self else { return }
                switch status {
                case .readyToPlay:
                    self.duration = playerItem.duration.seconds.isFinite ? playerItem.duration.seconds : self.stream.durationInSeconds
                    if self.state == .loading {
                        self.state = .ready
                        self.play()
                    }
                case .failed:
                    let errorMsg = playerItem.error?.localizedDescription ?? "Unable to play this video stream."
                    self.state = .failed(errorMsg)
                default:
                    break
                }
            }
            .store(in: &cancellables)
        
        // Observe end of media
        NotificationCenter.default.publisher(for: .AVPlayerItemDidPlayToEndTime, object: playerItem)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self else { return }
                self.state = .finished
                self.savePlaybackProgress()
            }
            .store(in: &cancellables)
        
        // Add periodic time observer
        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        timeObserverToken = newPlayer.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self else { return }
            self.currentTime = time.seconds
            self.savePlaybackProgress()
        }
        
        scheduleAutoHideControls()
    }
    
    public func play() {
        guard let player else { return }
        player.play()
        state = .playing
        scheduleAutoHideControls()
    }
    
    public func pause() {
        guard let player else { return }
        player.pause()
        state = .paused
        savePlaybackProgress()
    }
    
    public func togglePlayPause() {
        if state == .playing {
            pause()
        } else {
            play()
        }
    }
    
    public func seek(to timeInSeconds: Double) {
        guard let player else { return }
        let cmTime = CMTime(seconds: timeInSeconds, preferredTimescale: 600)
        player.seek(to: cmTime) { [weak self] _ in
            guard let self else { return }
            self.currentTime = timeInSeconds
            self.savePlaybackProgress()
        }
    }
    
    public func seekForward(seconds: Double = 10.0) {
        let newTime = min(currentTime + seconds, duration)
        seek(to: newTime)
    }
    
    public func seekBackward(seconds: Double = 10.0) {
        let newTime = max(currentTime - seconds, 0.0)
        seek(to: newTime)
    }
    
    public func toggleControls() {
        isControlsVisible.toggle()
        if isControlsVisible {
            scheduleAutoHideControls()
        }
    }
    
    public func selectSubtitleTrack(_ track: SubtitleTrack?) {
        selectedSubtitleTrack = track
    }
    
    private func scheduleAutoHideControls() {
        controlsTimer?.invalidate()
        controlsTimer = Timer.scheduledTimer(withTimeInterval: 4.0, repeats: false) { [weak self] _ in
            Task { @MainActor in
                if self?.state == .playing {
                    self?.isControlsVisible = false
                }
            }
        }
    }
    
    private func savePlaybackProgress() {
        PlaybackProgressStore.shared.saveProgress(
            contentID: stream.contentID,
            positionInSeconds: currentTime,
            totalDurationInSeconds: duration
        )
    }
    
    deinit {
        if let timeObserverToken, let player {
            player.removeTimeObserver(timeObserverToken)
        }
        controlsTimer?.invalidate()
    }
}
