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
                    RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium)
                        .fill(AppColors.cardSurface)
                        .aspectRatio(16/9, contentMode: .fit)
                        .overlay(
                            ZStack {
                                LinearGradient(
                                    colors: [AppColors.secondary.opacity(0.4), AppColors.cardSurface],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                                
                                Circle()
                                    .fill(Color.black.opacity(0.5))
                                    .frame(width: 40, height: 40)
                                    .overlay(
                                        Image(systemName: "play.fill")
                                            .font(.system(size: 18))
                                            .foregroundStyle(AppColors.primary)
                                            .offset(x: 2)
                                    )
                            }
                        )
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
            .frame(width: 200)
        }
        .buttonStyle(.cardPress)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(item.title), \(item.subtitleMetadata)")
    }
}

#Preview {
    ContinueWatchingCardView(
        item: ContentItem(
            id: "1",
            title: "Roots of Culture: Ep 3",
            description: "Preserving ancient weaving techniques",
            category: "Documentary",
            type: .documentary,
            progress: 0.65
        ),
        action: {}
    )
}
