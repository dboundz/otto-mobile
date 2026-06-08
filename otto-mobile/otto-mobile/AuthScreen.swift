import SwiftUI

struct AuthScreen: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.openURL) private var openURL
    @State private var phoneNumber: String = ""
    @State private var displayName: String = ""
    @State private var otpCode: String = ""
    @State private var signupInviteCode: String = ""
    @State private var otpRequested = false
    @State private var isBusy = false

    private var showingLegacyOnboardingName: Bool {
        appState.requiresOnboardingName && appState.signupAfterOtpStep == nil
    }

    private var showingSignupInvite: Bool {
        appState.signupAfterOtpStep == .inviteCode
    }

    private var showingSignupDisplayName: Bool {
        appState.signupAfterOtpStep == .displayName
    }

    private var showingPhoneAndOtp: Bool {
        !showingLegacyOnboardingName && !showingSignupInvite && !showingSignupDisplayName
    }

    var body: some View {
        ZStack {
            Color.black
                .ignoresSafeArea()

            VStack(spacing: 22) {
                Image("SignInLogo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 240)
                    .padding(.top, 8)

                VStack(alignment: .leading, spacing: 14) {
                    Text("Sign in")
                        .font(.title.bold())
                        .foregroundStyle(.white)

                    Text(authSubtitle)
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.78))

                    VStack(spacing: 10) {
                        if showingLegacyOnboardingName || showingSignupDisplayName {
                            neonField("Your name", text: $displayName)
                                .onChange(of: displayName) { _, _ in clearAuthError() }
                        } else if showingSignupInvite {
                            neonField(
                                "Invite code",
                                text: $signupInviteCode,
                                keyboard: .asciiCapable,
                                autocapitalization: .characters
                            )
                            .onChange(of: signupInviteCode) { _, _ in clearAuthError() }
                        } else {
                            neonField("US phone number", text: $phoneNumber, keyboard: .phonePad)
                                .onChange(of: phoneNumber) { _, newValue in
                                    clearAuthError()
                                    guard otpRequested else { return }
                                    let newTrimmed = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
                                    let sentFor =
                                        appState.authPhoneNumber.trimmingCharacters(in: .whitespacesAndNewlines)
                                    guard !sentFor.isEmpty else {
                                        otpRequested = false
                                        otpCode = ""
                                        return
                                    }
                                    if newTrimmed != sentFor {
                                        otpRequested = false
                                        otpCode = ""
                                        appState.authPhoneNumber = ""
                                        appState.errorMessage = nil
                                    }
                                }
                        }
                        if otpRequested && showingPhoneAndOtp {
                            neonField("6-digit code", text: $otpCode, keyboard: .numberPad)
                                .onChange(of: otpCode) { _, _ in clearAuthError() }
                        }
                    }

                    if let error = appState.errorMessage, !error.isEmpty {
                        Text(error)
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(Color.red.opacity(0.92))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 2)
                            .accessibilityIdentifier("auth-error-message")
                    }

                    Button(isBusy ? "Please wait..." : buttonTitle) {
                        Task {
                            isBusy = true
                            defer { isBusy = false }
                            if showingLegacyOnboardingName {
                                await appState.completeOnboardingName(displayName)
                            } else if showingSignupInvite {
                                await appState.advanceSignupPastInvite(code: signupInviteCode)
                            } else if showingSignupDisplayName {
                                await appState.completeSignupWithDisplayName(displayName)
                            } else if otpRequested {
                                await appState.verifyAuthOTP(code: otpCode)
                            } else {
                                let didRequest = await appState.requestAuthOTP(phoneNumber: phoneNumber)
                                if didRequest {
                                    otpRequested = true
                                }
                            }
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .primaryCTAButtonStyle(horizontalPadding: 16, verticalPadding: 12)
                    .disabled(isBusy)
                    .opacity(isBusy ? 0.8 : 1)
                }
                .padding(16)
                .background(Color.black.opacity(0.72))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.white.opacity(0.18), lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .frame(maxWidth: 360)
                .onChange(of: appState.signupAfterOtpStep) { _, newValue in
                    if newValue != nil {
                        otpRequested = false
                        clearAuthError()
                    }
                }

                HStack(spacing: 0) {
                    legalFooterButton("Privacy Policy", url: WebsiteLinks.privacyPolicy)
                    Text("  ·  ")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.35))
                    legalFooterButton("Terms of Use", url: WebsiteLinks.termsOfUse)
                }
                .padding(.top, 4)
            }
            .padding(.horizontal, 20)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
    }

    private var authSubtitle: String {
        if showingLegacyOnboardingName {
            return "One more step: choose your display name."
        }
        if showingSignupInvite {
            return "Enter your invite code."
        }
        if showingSignupDisplayName {
            return "Choose your display name."
        }
        return "Use your US phone number to get a one-time code."
    }

    private func neonField(
        _ placeholder: String,
        text: Binding<String>,
        keyboard: UIKeyboardType = .default,
        autocapitalization: TextInputAutocapitalization = .never
    ) -> some View {
        TextField(placeholder, text: text)
            .keyboardType(keyboard)
            .textInputAutocapitalization(autocapitalization)
            .disableAutocorrection(true)
            .padding(.horizontal, 12)
            .padding(.vertical, 11)
            .foregroundStyle(.white)
            .background(Color.white.opacity(0.06))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(Color(red: 0.85, green: 0.25, blue: 1).opacity(0.5), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .shadow(color: Color(red: 0.72, green: 0.24, blue: 1).opacity(0.25), radius: 8, y: 2)
    }

    private var buttonTitle: String {
        if showingLegacyOnboardingName { return "Continue" }
        if showingSignupInvite { return "Continue" }
        if showingSignupDisplayName { return "Join Driftd" }
        return otpRequested ? "Verify code" : "Send code"
    }

    private func clearAuthError() {
        if appState.errorMessage != nil {
            appState.errorMessage = nil
        }
    }

    private func legalFooterButton(_ title: String, url: URL) -> some View {
        Button(title) {
            openURL(url)
        }
        .font(.caption)
        .foregroundStyle(.white.opacity(0.55))
        .buttonStyle(.plain)
    }
}
