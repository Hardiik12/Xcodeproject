//
//  VideoPlayerView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI
import AVKit

public struct VideoPlayerView: View {
    @StateObject private var viewModel: VideoPlayerViewModel
    let contentTitle: String
    let onClose: () -> Void
    
    public init(stream: MediaStream, contentTitle: String = "CommunityOTT Stream", onClose: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: VideoPlayerViewModel(stream: stream))
        self.contentTitle = contentTitle
        self.onClose = onClose
    }
    
    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            // Native SwiftUI VideoPlayer
            if let player = viewModel.player {
                VideoPlayer(player: player)
                    .ignoresSafeArea()
                    .onTapGesture {
                        viewModel.toggleControls()
                    }
            }
            
            // Custom Fullscreen Overlay Controls
            if viewModel.isControlsVisible {
                controlsOverlay
                    .transition(.opacity)
            }
            
            // Loading State
            if viewModel.state == .loading {
                LoadingView(message: "Buffering Stream...")
            }
            
            // Error State
            if case .failed(let errorMsg) = viewModel.state {
                playerErrorView(message: errorMsg)
            }
        }
        .onAppear {
            viewModel.setupPlayer()
        }
        .onDisappear {
            viewModel.pause()
        }
        .preferredColorScheme(.dark)
    }
    
    private var controlsOverlay: some View {
        ZStack {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .onTapGesture {
                    viewModel.toggleControls()
                }
            
            VStack {
                // Top Header Controls
                HStack {
                    Button(action: onClose) {
                        Image(systemName: "xmark")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(AppColors.textPrimary)
                            .padding(10)
                            .background(Color.black.opacity(0.6))
                            .clipShape(Circle())
                    }
                    .accessibilityLabel("Close video player")
                    
                    Text(contentTitle)
                        .font(AppTypography.headline)
                        .foregroundStyle(AppColors.textPrimary)
                        .lineLimit(1)
                        .padding(.leading, AppSpacing.small)
                    
                    Spacer()
                    
                    // Subtitles Menu
                    if !viewModel.stream.subtitleTracks.isEmpty {
                        Menu {
                            Button("Off") {
                                viewModel.selectSubtitleTrack(nil)
                            }
                            ForEach(viewModel.stream.subtitleTracks) { track in
                                Button(track.languageName) {
                                    viewModel.selectSubtitleTrack(track)
                                }
                            }
                        } label: {
                            Image(systemName: "captions.bubble.fill")
                                .font(.system(size: 18))
                                .foregroundStyle(viewModel.selectedSubtitleTrack != nil ? AppColors.primary : AppColors.textPrimary)
                                .padding(10)
                                .background(Color.black.opacity(0.6))
                                .clipShape(Circle())
                        }
                        .accessibilityLabel("Subtitles menu")
                    }
                }
                .padding(.horizontal, AppSpacing.medium)
                .padding(.top, AppSpacing.large)
                
                Spacer()
                
                // Middle Transport Controls
                HStack(spacing: AppSpacing.xxLarge) {
                    Button {
                        viewModel.seekBackward(seconds: 10)
                    } label: {
                        Image(systemName: "goforward.10")
                            .rotationEffect(.degrees(180))
                            .font(.system(size: 28, weight: .semibold))
                            .foregroundStyle(AppColors.textPrimary)
                    }
                    .accessibilityLabel("Seek backward 10 seconds")
                    
                    Button {
                        viewModel.togglePlayPause()
                    } label: {
                        Image(systemName: viewModel.state == .playing ? "pause.circle.fill" : "play.circle.fill")
                            .font(.system(size: 64))
                            .foregroundStyle(AppColors.primary)
                    }
                    .accessibilityLabel(viewModel.state == .playing ? "Pause" : "Play")
                    
                    Button {
                        viewModel.seekForward(seconds: 10)
                    } label: {
                        Image(systemName: "goforward.10")
                            .font(.system(size: 28, weight: .semibold))
                            .foregroundStyle(AppColors.textPrimary)
                    }
                    .accessibilityLabel("Seek forward 10 seconds")
                }
                
                Spacer()
                
                // Bottom Timeline & Scrubber Bar
                VStack(spacing: AppSpacing.xxSmall) {
                    Slider(
                        value: Binding(
                            get: { viewModel.currentTime },
                            set: { newValue in
                                viewModel.seek(to: newValue)
                            }
                        ),
                        in: 0...max(viewModel.duration, 1.0)
                    )
                    .tint(AppColors.primary)
                    
                    HStack {
                        Text(formatTime(viewModel.currentTime))
                            .font(AppTypography.caption)
                            .foregroundStyle(AppColors.textSecondary)
                        
                        Spacer()
                        
                        Text(formatTime(viewModel.duration))
                            .font(AppTypography.caption)
                            .foregroundStyle(AppColors.textSecondary)
                    }
                }
                .padding(.horizontal, AppSpacing.medium)
                .padding(.bottom, AppSpacing.xLarge)
            }
        }
    }
    
    @ViewBuilder
    private func playerErrorView(message: String) -> some View {
        ZStack {
            AppColors.background.ignoresSafeArea()
            
            VStack(spacing: AppSpacing.medium) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 48))
                    .foregroundStyle(AppColors.secondary)
                
                Text("Unable to play this video")
                    .font(AppTypography.title1)
                    .foregroundStyle(AppColors.textPrimary)
                
                Text(message)
                    .font(AppTypography.body)
                    .foregroundStyle(AppColors.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, AppSpacing.large)
                
                PrimaryButton(title: "Try Again", iconSystemName: "arrow.clockwise") {
                    viewModel.setupPlayer()
                }
                .frame(maxWidth: 200)
            }
        }
    }
    
    private func formatTime(_ seconds: Double) -> String {
        guard seconds.isFinite && !seconds.isNaN else { return "00:00" }
        let totalSeconds = Int(seconds)
        let mins = totalSeconds / 60
        let secs = totalSeconds % 60
        return String(format: "%02d:%02d", mins, secs)
    }
}
