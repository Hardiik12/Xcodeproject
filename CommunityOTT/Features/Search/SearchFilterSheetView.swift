//
//  SearchFilterSheetView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI

public enum ContentFilterType: String, CaseIterable, Identifiable, Sendable {
    case all = "All"
    case documentary = "Documentary"
    case podcast = "Podcast"
    case story = "Story"
    case education = "Education"
    
    public var id: String { rawValue }
}

public struct SearchFilterSheetView: View {
    @Binding var selectedType: ContentFilterType
    @Binding var selectedCategory: String
    @Binding var selectedLanguage: String
    
    let onClear: () -> Void
    let onApply: () -> Void
    
    @Environment(\.dismiss) private var dismiss
    
    public static let categories = [
        "All",
        "Folk & Cultural Arts",
        "History & Documentaries",
        "Empowerment",
        "Voices of Success",
        "Podcasts",
        "Education & Skills"
    ]
    
    public static let languages = [
        "All",
        "English",
        "Telugu"
    ]
    
    public init(
        selectedType: Binding<ContentFilterType>,
        selectedCategory: Binding<String>,
        selectedLanguage: Binding<String>,
        onClear: @escaping () -> Void,
        onApply: @escaping () -> Void
    ) {
        self._selectedType = selectedType
        self._selectedCategory = selectedCategory
        self._selectedLanguage = selectedLanguage
        self.onClear = onClear
        self.onApply = onApply
    }
    
    public var body: some View {
        NavigationStack {
            ZStack {
                AppColors.background.ignoresSafeArea()
                
                ScrollView {
                    VStack(alignment: .leading, spacing: AppSpacing.large) {
                        // 1. Content Type Filter
                        filterSectionHeader(title: "Content Type")
                        chipPicker(
                            items: ContentFilterType.allCases.map { $0.rawValue },
                            selected: selectedType.rawValue
                        ) { val in
                            if let type = ContentFilterType(rawValue: val) {
                                selectedType = type
                            }
                        }
                        
                        Divider().background(Color.white.opacity(0.1))
                        
                        // 2. Category Filter
                        filterSectionHeader(title: "Category")
                        chipPicker(
                            items: Self.categories,
                            selected: selectedCategory
                        ) { cat in
                            selectedCategory = cat
                        }
                        
                        Divider().background(Color.white.opacity(0.1))
                        
                        // 3. Language Filter
                        filterSectionHeader(title: "Language")
                        chipPicker(
                            items: Self.languages,
                            selected: selectedLanguage
                        ) { lang in
                            selectedLanguage = lang
                        }
                    }
                    .padding(AppSpacing.medium)
                    .padding(.bottom, 40)
                }
            }
            .navigationTitle("Filter Content")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Clear") {
                        onClear()
                    }
                    .font(AppTypography.subheadline)
                    .foregroundStyle(AppColors.textSecondary)
                    .accessibilityLabel("Clear search filters")
                }
                
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Apply") {
                        onApply()
                        dismiss()
                    }
                    .font(AppTypography.headline)
                    .foregroundStyle(AppColors.primary)
                    .accessibilityLabel("Apply search filters")
                }
            }
        }
        .preferredColorScheme(.dark)
    }
    
    private func filterSectionHeader(title: String) -> some View {
        Text(title)
            .font(AppTypography.headline)
            .foregroundStyle(AppColors.primary)
    }
    
    private func chipPicker(items: [String], selected: String, onSelect: @escaping (String) -> Void) -> some View {
        FlowLayout(spacing: AppSpacing.small) {
            ForEach(items, id: \.self) { item in
                let isSelected = selected == item
                Button {
                    onSelect(item)
                } label: {
                    Text(item)
                        .font(AppTypography.subheadline)
                        .padding(.horizontal, AppSpacing.medium)
                        .padding(.vertical, AppSpacing.xSmall)
                        .background(isSelected ? AppColors.primary : AppColors.cardSurface)
                        .foregroundStyle(isSelected ? Color.black : AppColors.textPrimary)
                        .clipShape(Capsule())
                        .overlay(
                            Capsule()
                                .stroke(isSelected ? AppColors.primary : Color.white.opacity(0.15), lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Filter option \(item), \(isSelected ? "selected" : "not selected")")
            }
        }
    }
}

// Simple FlowLayout helper for wrapping chip views
struct FlowLayout: Layout {
    var spacing: CGFloat
    
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 300
        var height: CGFloat = 0
        var x: CGFloat = 0
        var y: CGFloat = 0
        var maxHeight: CGFloat = 0
        
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > width {
                x = 0
                y += maxHeight + spacing
                maxHeight = 0
            }
            x += size.width + spacing
            maxHeight = max(maxHeight, size.height)
        }
        height = y + maxHeight
        return CGSize(width: width, height: height)
    }
    
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x: CGFloat = bounds.minX
        var y: CGFloat = bounds.minY
        var maxHeight: CGFloat = 0
        
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX {
                x = bounds.minX
                y += maxHeight + spacing
                maxHeight = 0
            }
            subview.place(at: CGPoint(x: x, y: y), proposal: .unspecified)
            x += size.width + spacing
            maxHeight = max(maxHeight, size.height)
        }
    }
}
