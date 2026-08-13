//
//  ContentDetailsView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

@MainActor
public struct ContentDetailsView: View {
    @StateObject private var viewModel: ContentDetailsViewModel
    @Environment(\.dismiss) private var dismiss
    
    public init(item: ContentItem) {
        _viewModel = StateObject(wrappedValue: ContentDetailsViewModel(item: item))
    }
    
    public var body: some View {
        ZStack {
            AppColors.background.ignoresSafeArea()
            
            if viewModel.isLoading {
                LoadingView(message: "Loading Content Details...")
            } else if let errorMsg = viewModel.errorMessage {
                ErrorStateView(message: errorMsg) {
                    Task {
                        await viewModel.loadDetails()
                    }
                }
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: AppSpacing.large) {
                        // Header Visual Backdrop with Back Button
                        ZStack(alignment: .topLeading) {
                            ZStack(alignment: .bottom) {
                                if let imageName = viewModel.item.imageName {
                                    Image(imageName)
                                        .resizable()
                                        .aspectRatio(contentMode: .fill)
                                        .frame(height: 320)
                                        .clipped()
                                } else {
                                    LinearGradient(
                                        colors: [AppColors.secondary, AppColors.cardSurface],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                    .frame(height: 320)
                                }
                                
                                LinearGradient(
                                    colors: [Color.clear, AppColors.background.opacity(0.8), AppColors.background],
                                    startPoint: .top,
                                    endPoint: .bottom
                                )
                            }
                            
                            // Back Button
                            Button {
                                dismiss()
                            } label: {
                                Image(systemName: "chevron.left")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundStyle(AppColors.textPrimary)
                                    .padding(12)
                                    .background(Color.black.opacity(0.6))
                                    .clipShape(Circle())
                            }
                            .padding(.leading, AppSpacing.medium)
                            .padding(.top, AppSpacing.large + 8)
                            .accessibilityLabel("Back to Home")
                        }
                        
                        // Main Metadata & Header Titles
                        VStack(alignment: .leading, spacing: AppSpacing.xSmall) {
                            // Category Badge
                            Text(viewModel.item.category.uppercased())
                                .font(AppTypography.badge)
                                .padding(.horizontal, AppSpacing.small)
                                .padding(.vertical, AppSpacing.xxSmall)
                                .background(AppColors.secondary)
                                .foregroundStyle(AppColors.textPrimary)
                                .cornerRadius(AppSpacing.CornerRadius.small)
                            
                            // Title
                            Text(viewModel.item.title)
                                .font(AppTypography.heroTitle)
                                .foregroundStyle(AppColors.textPrimary)
                            
                            // Subtitle Metadata Line
                            Text("\(viewModel.item.type.rawValue.capitalized) • \(viewModel.item.language) • \(viewModel.item.durationFormatted) • \(viewModel.item.releaseYear)")
                                .font(AppTypography.subheadline)
                                .foregroundStyle(AppColors.textSecondary)
                        }
                        .padding(.horizontal, AppSpacing.medium)
                        
                        // CTA Action Buttons
                        HStack(spacing: AppSpacing.medium) {
                            PrimaryButton(title: "Watch Now", iconSystemName: "play.fill") {
                                withAnimation(.easeInOut(duration: 0.3)) {
                                    viewModel.isPlayingVideo = true
                                }
                            }
                            .accessibilityLabel("Watch Now")
                            
                            SecondaryButton(
                                title: viewModel.isSavedToList ? "In My List" : "My List",
                                iconSystemName: viewModel.isSavedToList ? "checkmark" : "plus"
                            ) {
                                viewModel.toggleMyList()
                            }
                            .accessibilityLabel(viewModel.isSavedToList ? "Remove from My List" : "Add to My List")
                        }
                        .padding(.horizontal, AppSpacing.medium)
                        
                        // About Section
                        VStack(alignment: .leading, spacing: AppSpacing.small) {
                            Text("About")
                                .font(AppTypography.title2)
                                .foregroundStyle(AppColors.textPrimary)
                            
                            Text(viewModel.item.description)
                                .font(AppTypography.body)
                                .foregroundStyle(AppColors.textSecondary)
                                .lineSpacing(4)
                            
                            // Contributor Metadata Grid
                            VStack(alignment: .leading, spacing: AppSpacing.xSmall) {
                                HStack {
                                    Text("Category:")
                                        .font(AppTypography.subheadline)
                                        .foregroundStyle(AppColors.textMuted)
                                    Text(viewModel.item.category)
                                        .font(AppTypography.subheadline)
                                        .foregroundStyle(AppColors.textPrimary)
                                }
                                
                                HStack {
                                    Text("Audio & Subtitles:")
                                        .font(AppTypography.subheadline)
                                        .foregroundStyle(AppColors.textMuted)
                                    Text("Telugu, English")
                                        .font(AppTypography.subheadline)
                                        .foregroundStyle(AppColors.textPrimary)
                                }
                                
                                HStack {
                                    Text("Cultural Preservation:")
                                        .font(AppTypography.subheadline)
                                        .foregroundStyle(AppColors.textMuted)
                                    Text("Community Heritage Project")
                                        .font(AppTypography.subheadline)
                                        .foregroundStyle(AppColors.primary)
                                }
                            }
                            .padding(.top, AppSpacing.xSmall)
                        }
                        .padding(.horizontal, AppSpacing.medium)
                        
                        // More Like This Section
                        if !viewModel.relatedItems.isEmpty {
                            ContentRailView(
                                title: "More Like This",
                                subtitle: "Explore related heritage titles",
                                items: viewModel.relatedItems,
                                variant: .poster,
                                onSelect: { selectedRelated in
                                    // Select related content
                                }
                            )
                            .padding(.top, AppSpacing.small)
                        }
                    }
                    .padding(.bottom, 60)
                }
            }
            
            // Full Screen Dedicated Video Player Overlay
            if viewModel.isPlayingVideo, let stream = viewModel.mediaStream {
                VideoPlayerView(
                    stream: stream,
                    contentTitle: viewModel.item.title,
                    onClose: {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            viewModel.isPlayingVideo = false
                        }
                    }
                )
                .transition(.move(edge: .bottom))
                .zIndex(100)
            }
        }
        .task {
            await viewModel.loadDetails()
        }
        .navigationBarBackButtonHidden(true)
    }
}

#Preview {
    ContentDetailsView(
        item: ContentItem(
            id: "hero-1",
            title: "Stories of Heritage",
            description: "Discover stories that deserve to be remembered.",
            category: "Documentary",
            type: .documentary,
            imageName: "hero_heritage"
        )
    )
}
