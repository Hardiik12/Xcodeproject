//
//  CategoryContentView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct CategoryContentView: View {
    let category: ContentCategory
    let items: [ContentItem]
    let onBack: () -> Void
    let onSelectItem: (ContentItem) -> Void
    
    private let columns = [
        GridItem(.flexible(), spacing: AppSpacing.medium),
        GridItem(.flexible(), spacing: AppSpacing.medium)
    ]
    
    public init(
        category: ContentCategory,
        items: [ContentItem],
        onBack: @escaping () -> Void,
        onSelectItem: @escaping (ContentItem) -> Void
    ) {
        self.category = category
        self.items = items
        self.onBack = onBack
        self.onSelectItem = onSelectItem
    }
    
    public var body: some View {
        ZStack {
            AppColors.background.ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Custom Navigation Header
                HStack(spacing: AppSpacing.medium) {
                    Button(action: onBack) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(AppColors.textPrimary)
                            .padding(10)
                            .background(AppColors.cardSurface)
                            .clipShape(Circle())
                    }
                    .accessibilityLabel("Back to Discover")
                    
                    VStack(alignment: .leading, spacing: AppSpacing.xxSmall) {
                        Text(category.title)
                            .font(AppTypography.headline)
                            .foregroundStyle(AppColors.textPrimary)
                            .lineLimit(1)
                        
                        Text("\(items.count) \(items.count == 1 ? "Story" : "Stories")")
                            .font(AppTypography.caption)
                            .foregroundStyle(AppColors.primary)
                    }
                    
                    Spacer()
                }
                .padding(.horizontal, AppSpacing.medium)
                .padding(.top, AppSpacing.medium)
                .padding(.bottom, AppSpacing.small)
                
                ScrollView {
                    VStack(alignment: .leading, spacing: AppSpacing.large) {
                        // Category Header Banner
                        VStack(alignment: .leading, spacing: AppSpacing.xSmall) {
                            Text(category.description)
                                .font(AppTypography.body)
                                .foregroundStyle(AppColors.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(.horizontal, AppSpacing.medium)
                        
                        // Content Grid
                        if items.isEmpty {
                            EmptyStateView(
                                title: "No Stories Found",
                                description: "Content for \(category.title) will be released soon."
                            )
                            .padding(.top, AppSpacing.xxLarge)
                        } else {
                            LazyVGrid(columns: columns, spacing: AppSpacing.medium) {
                                ForEach(items) { item in
                                    PosterCardView(item: item) {
                                        onSelectItem(item)
                                    }
                                }
                            }
                            .padding(.horizontal, AppSpacing.medium)
                        }
                    }
                    .padding(.bottom, 100) // Bottom inset for floating tab bar
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}
