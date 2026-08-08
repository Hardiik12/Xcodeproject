//
//  ContinueWatchingCardView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct ContinueWatchingCardView: View {
    let item: ContentItem
    let action: () -> Void
    
    public init(item: ContentItem, action: @escaping () -> Void) {
        self.item = item
        self.action = action
    }
    
    public var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: AppSpacing.xxSmall) {
                // Card Media Surface
                ZStack(alignment: .bottom) {
                    ZStack {
                        if let imageName = item.imageName {
                            Image(imageName)
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                        } else {
                            LinearGradient(
                                colors: [AppColors.secondary.opacity(0.6), AppColors.cardSurface],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        }
                        
                        // Play Icon Overlay
                        Circle()
                            .fill(Color.black.opacity(0.45))
                            .frame(width: 36, height: 36)
                            .overlay(
                                Image(systemName: "play.fill")
                                    .font(.system(size: 16))
                                    .foregroundStyle(AppColors.primary)
                                    .offset(x: 1.5)
                            )
                    }
                    .frame(width: 190, height: 107) // 16:9 aspect ratio
                    .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
                    
                    // Progress Bar Indicator
                    if let progress = item.progress {
                        GeometryReader { geometry in
                            ZStack(alignment: .leading) {
                                Rectangle()
                                    .fill(Color.white.opacity(0.2))
                                
                                Rectangle()
                                    .fill(AppColors.primary)
                                    .frame(width: geometry.size.width * CGFloat(progress))
                            }
                        }
                        .frame(height: 3)
                        .clipShape(RoundedRectangle(cornerRadius: 2))
                    }
                }
                
                Text(item.title)
                    .font(AppTypography.subheadline)
                    .foregroundStyle(AppColors.textPrimary)
                    .lineLimit(1)
                
                Text(item.subtitleMetadata)
                    .font(AppTypography.caption)
                    .foregroundStyle(AppColors.textSecondary)
                    .lineLimit(1)
            }
            .frame(width: 190)
        }
        .buttonStyle(.cardPress)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(item.title), \(item.subtitleMetadata)")
    }
}
