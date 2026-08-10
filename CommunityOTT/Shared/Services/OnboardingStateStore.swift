//
//  OnboardingStateStore.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import Foundation
import Combine

public final class OnboardingStateStore: ObservableObject, @unchecked Sendable {
    public static let shared = OnboardingStateStore()
    
    private static let completedKey = "communityott_has_completed_onboarding"
    private static let interestsKey = "communityott_selected_interests"
    
    @Published public var hasCompletedOnboarding: Bool {
        didSet {
            UserDefaults.standard.set(hasCompletedOnboarding, forKey: Self.completedKey)
        }
    }
    
    @Published public var selectedInterests: Set<String> {
        didSet {
            UserDefaults.standard.set(Array(selectedInterests), forKey: Self.interestsKey)
        }
    }
    
    private init() {
        let completed = UserDefaults.standard.bool(forKey: Self.completedKey)
        let interestsArr = UserDefaults.standard.stringArray(forKey: Self.interestsKey) ?? []
        
        self.hasCompletedOnboarding = completed
        self.selectedInterests = Set(interestsArr)
    }
    
    public func completeOnboarding() {
        DispatchQueue.main.async { [weak self] in
            self?.hasCompletedOnboarding = true
        }
    }
    
    public func saveInterests(_ interests: Set<String>) {
        DispatchQueue.main.async { [weak self] in
            self?.selectedInterests = interests
        }
    }
    
    public func resetOnboardingForTesting() {
        DispatchQueue.main.async { [weak self] in
            self?.hasCompletedOnboarding = false
            self?.selectedInterests = []
        }
    }
}
