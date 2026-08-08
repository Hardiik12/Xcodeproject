//
//  HeroBannerView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct HeroBannerView: View {
    let item: ContentItem
    let onWatch: () -> Void
    let onMyList: () -> Void
    
    @State private var isVisible = false
    
    public init(
        item: ContentItem,
        onWatch: @escaping () -> Void,
        onMyList: @escaping () -> Void
    ) {
        self.item = item
        self.onWatch = onWatch
        self.onMyList = onMyList
    }
    
    public var body: some View {
        ZStack(alignment: .bottomLeading) {
            // Background Image (Remote URL -> Local Asset -> Gradient Fallback)
            ZStack {
                if let bannerURLString = item.bannerURL ?? item.posterURL,
                   let url = URL(string: bannerURLString) {
                    AsyncImage(url: url) { image in
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(maxWidth: .infinity)
                            .frame(height: 220)
                            .clipped()
                    } placeholder: {
                        heroLocalImage(height: 220)
                    }
                } else {
                    heroLocalImage(height: 220)
                }
                
                // Dark Cinematic Vignette & Gradient Overlay
                LinearGradient(
                    colors: [
                        Color.black.opacity(0.3),
                        Color.clear,
                        AppColors.background.opacity(0.85),
                        AppColors.background
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
            .frame(height: 220)
            
            // Hero Content Overlay
            VStack(alignment: .leading, spacing: AppSpacing.xxSmall + 2) {
                // Category Badge
                Text(item.category.uppercased())
                    .font(AppTypography.badge)
                    .padding(.horizontal, AppSpacing.small)
                    .padding(.vertical, AppSpacing.xxSmall)
                    .background(AppColors.secondary)
                    .foregroundStyle(AppColors.textPrimary)
                    .cornerRadius(AppSpacing.CornerRadius.small)
                
                // Title
                Text(item.title)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(AppColors.textPrimary)
                    .lineLimit(1)
                
                // Metadata Line
                Text("\(item.type.rawValue.capitalized) • \(item.durationFormatted) • \(item.language)")
                    .font(AppTypography.caption)
                    .foregroundStyle(AppColors.textSecondary)
                
                // Short Description (Max 2 lines)
                Text(item.description)
                    .font(AppTypography.footnote)
                    .foregroundStyle(AppColors.textSecondary.opacity(0.9))
                    .lineLimit(2)
                    .padding(.trailing, AppSpacing.medium)
                
                // Action Buttons
                HStack(spacing: AppSpacing.small) {
                    Button(action: onWatch) {
                        HStack(spacing: AppSpacing.xxSmall + 2) {
                            Image(systemName: "play.fill")
                                .font(.system(size: 13, weight: .semibold))
                            Text("Watch")
                                .font(AppTypography.headline)
                        }
                        .padding(.horizontal, AppSpacing.medium)
                        .padding(.vertical, AppSpacing.xxSmall + 4)
                        .background(AppColors.primary)
                        .foregroundStyle(Color.black)
                        .cornerRadius(AppSpacing.CornerRadius.medium)
                    }
                    .buttonStyle(.cardPress)
                    
                    Button(action: onMyList) {
                        HStack(spacing: AppSpacing.xxSmall + 2) {
                            Image(systemName: "plus")
                                .font(.system(size: 13, weight: .semibold))
                            Text("My List")
                                .font(AppTypography.headline)
                        }
                        .padding(.horizontal, AppSpacing.medium)
                        .padding(.vertical, AppSpacing.xxSmall + 4)
                        .background(AppColors.cardSurface.opacity(0.85))
                        .foregroundStyle(AppColors.textPrimary)
                        .overlay(
                            RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium)
                                .stroke(Color.white.opacity(0.15), lineWidth: 1)
                        )
                        .cornerRadius(AppSpacing.CornerRadius.medium)
                    }
                    .buttonStyle(.cardPress)
                }
                .padding(.top, AppSpacing.xxSmall)
            }
            .padding(.horizontal, AppSpacing.medium)
            .padding(.bottom, AppSpacing.xSmall)
        }
        .frame(height: 220)
        .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.large))
        .padding(.horizontal, AppSpacing.small)
        .opacity(isVisible ? 1.0 : 0.0)
        .onAppear {
            withAnimation(.easeOut(duration: 0.35)) {
                isVisible = true
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Hero Feature: \(item.title), \(item.category). \(item.description)")
    }
    
    @ViewBuilder
    private func heroLocalImage(height: CGFloat) -> some View {
        if let imageName = item.imageName {
            Image(imageName)
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(maxWidth: .infinity)
                .frame(height: height)
                .clipped()
        } else {
            Image("hero_heritage")
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(maxWidth: .infinity)
                .frame(height: height)
                .clipped()
        }
    }
}

#Preview {
    HeroBannerView(
        item: ContentItem(
            id: "hero-1",
            title: "Stories of Heritage",
            description: "Discover stories that deserve to be remembered. Explore deep cultural traditions and living legends.",
            category: "Documentary",
            type: .documentary,
            imageName: "hero_heritage"
        ),
        onWatch: {},
        onMyList: {}
    )
}
