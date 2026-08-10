//
//  OnboardingViewModel.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI
import Combine

@MainActor
public final class OnboardingViewModel: ObservableObject {
    @Published public var stepIndex: Int = 0
    @Published public var selectedInterests: Set<String> = []
    
    private let store: OnboardingStateStore
    
    public init(store: OnboardingStateStore = OnboardingStateStore.shared) {
        self.store = store
        self.selectedInterests = store.selectedInterests
    }
    
    public var totalSteps: Int { 3 }
    
    public var isFirstStep: Bool { stepIndex == 0 }
    public var isLastStep: Bool { stepIndex == totalSteps - 1 }
    
    public func nextStep(onFinish: () -> Void) {
        if isLastStep {
            finishOnboarding(onFinish: onFinish)
        } else {
            withAnimation(.easeInOut(duration: 0.3)) {
                stepIndex += 1
            }
        }
    }
    
    public func previousStep() {
        guard stepIndex > 0 else { return }
        withAnimation(.easeInOut(duration: 0.3)) {
            stepIndex -= 1
        }
    }
    
    public func skip(onFinish: () -> Void) {
        finishOnboarding(onFinish: onFinish)
    }
    
    public func finishOnboarding(onFinish: () -> Void) {
        store.saveInterests(selectedInterests)
        store.completeOnboarding()
        onFinish()
    }
}
