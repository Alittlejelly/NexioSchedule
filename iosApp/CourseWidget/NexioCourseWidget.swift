// xcode: set sdk=iOS

import ActivityKit
import SwiftUI
import WidgetKit

@main
struct NexioCourseWidgetBundle: WidgetBundle {
    var body: some Widget {
        NexioTodayCourseWidget()
        NexioLockScreenCourseWidget()
        NexioCourseLiveActivityWidget()
    }
}

// MARK: - Nexio App Icon (Vector gradient card)

struct NexioAppIconView: View {
    var size: CGFloat = 34

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.24, style: .continuous)
                .fill(Color.white)

            ZStack {
                RoundedRectangle(cornerRadius: size * 0.16, style: .continuous)
                    .strokeBorder(
                        LinearGradient(
                            colors: [
                                Color(red: 1.0, green: 0.45, blue: 0.13),
                                Color(red: 0.91, green: 0.44, blue: 0.74),
                                Color(red: 0.37, green: 0.43, blue: 0.93)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: size * 0.08
                    )

                VStack(alignment: .leading, spacing: size * 0.06) {
                    RoundedRectangle(cornerRadius: 1)
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color(red: 1.0, green: 0.45, blue: 0.13),
                                    Color(red: 0.91, green: 0.44, blue: 0.74)
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: size * 0.40, height: size * 0.07)

                    RoundedRectangle(cornerRadius: 1)
                        .fill(Color(red: 0.37, green: 0.43, blue: 0.93))
                        .frame(width: size * 0.26, height: size * 0.07)
                }
            }
            .frame(width: size * 0.68, height: size * 0.56)
        }
        .frame(width: size, height: size)
    }
}

// MARK: - Live Activity Widget

struct NexioCourseLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: NexioCourseActivityAttributes.self) { context in
            lockScreenView(context: context)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    expandedLeadingView(context: context)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    expandedTrailingView(context: context)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    expandedBottomView(context: context)
                }
            } compactLeading: {
                compactLeadingView(context: context)
            } compactTrailing: {
                compactTrailingView(context: context)
            } minimal: {
                minimalView(context: context)
            }
        }
    }

    // MARK: - Lock screen / StandBy (Super Island Style)

    private func lockScreenView(
        context: ActivityViewContext<NexioCourseActivityAttributes>
    ) -> some View {
        VStack(spacing: 12) {
            // Top section: Course name & subtitle | App icon
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(context.attributes.courseName)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(.white)
                        .lineLimit(1)

                    HStack(spacing: 6) {
                        if !context.state.section.isEmpty {
                            Text(context.state.section)
                        }
                        if !context.state.section.isEmpty && !context.state.classroom.isEmpty {
                            Text("|")
                                .foregroundStyle(Color.secondary.opacity(0.6))
                        }
                        if !context.state.classroom.isEmpty {
                            Text(context.state.classroom)
                        }
                    }
                    .font(.system(size: 14))
                    .foregroundStyle(.secondary)
                }

                Spacer(minLength: 8)

                NexioAppIconView(size: 34)
            }

            // Divider
            Rectangle()
                .fill(Color.white.opacity(0.12))
                .frame(height: 0.5)

            // Bottom section: Countdown | Location
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(context.state.isInClass ? "距离下课" : "即将上课")
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)

                    Text(
                        timerInterval: countdownRange(context: context),
                        countsDown: true
                    )
                    .font(.system(size: 22, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .contentTransition(.numericText(countsDown: true))
                    .multilineTextAlignment(.leading)
                    .foregroundStyle(.white)
                }
                .frame(minWidth: 84, alignment: .leading)

                Spacer(minLength: 16)

                VStack(alignment: .leading, spacing: 2) {
                    Text("地点")
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)

                    Text(context.state.classroom.isEmpty ? "未指定" : context.state.classroom)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                }

                Spacer()
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .activityBackgroundTint(Color(red: 0.08, green: 0.08, blue: 0.09))
    }

    // MARK: - Compact Dynamic Island

    @ViewBuilder
    private func compactLeadingView(
        context: ActivityViewContext<NexioCourseActivityAttributes>
    ) -> some View {
        slotView(mode: context.state.leftMode, isLeading: true, context: context)
    }

    @ViewBuilder
    private func compactTrailingView(
        context: ActivityViewContext<NexioCourseActivityAttributes>
    ) -> some View {
        if context.state.isInClass && context.state.inClassRightMode == 0 {
            HStack(spacing: 3) {
                Circle()
                    .fill(Color.green)
                    .frame(width: 6, height: 6)
                Text("上课中")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.green)
            }
            .fixedSize(horizontal: true, vertical: false)
        } else {
            slotView(mode: context.state.rightMode, isLeading: false, context: context)
        }
    }

    /// Slot text without icons to maximize horizontal space in compact pills.
    @ViewBuilder
    private func slotView(
        mode: Int,
        isLeading: Bool,
        context: ActivityViewContext<NexioCourseActivityAttributes>
    ) -> some View {
        switch mode {
        case 1:
            // Classroom location
            let room = context.state.classroom.isEmpty
                ? context.attributes.courseName
                : context.state.classroom
            Text(room)
                .font(.system(size: 13, weight: .semibold))
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
        case 2:
            // Timer text requires an explicit width.
            Text(
                timerInterval: countdownRange(context: context),
                countsDown: true
            )
            .monospacedDigit()
            .contentTransition(.numericText(countsDown: true))
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(context.state.isInClass ? Color.green : Color.orange)
            .multilineTextAlignment(isLeading ? .leading : .trailing)
            .frame(
                width: timerWidth(context: context, compact: true),
                alignment: isLeading ? .leading : .trailing
            )
        default:
            // Course Name
            Text(context.attributes.courseName)
                .font(.system(size: 13, weight: .semibold))
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
        }
    }

    // MARK: - Minimal Dynamic Island (Circular Countdown)

    private func minimalView(
        context: ActivityViewContext<NexioCourseActivityAttributes>
    ) -> some View {
        ProgressView(
            timerInterval: countdownRange(context: context),
            countsDown: true
        )
        .progressViewStyle(.circular)
        .tint(context.state.isInClass ? Color.green : Color.orange)
    }

    // MARK: - Expanded Dynamic Island (Super Island Optimized)

    private func expandedLeadingView(
        context: ActivityViewContext<NexioCourseActivityAttributes>
    ) -> some View {
        NexioAppIconView(size: 30)
            .padding(.leading, 2)
    }

    private func expandedTrailingView(
        context: ActivityViewContext<NexioCourseActivityAttributes>
    ) -> some View {
        VStack(alignment: .trailing, spacing: 2) {
            Text(
                timerInterval: countdownRange(context: context),
                countsDown: true
            )
            .font(.system(size: 20, weight: .bold, design: .rounded))
            .monospacedDigit()
            .contentTransition(.numericText(countsDown: true))
            .multilineTextAlignment(.trailing)
            .frame(width: timerWidth(context: context, compact: false), alignment: .trailing)
            .foregroundStyle(.white)

            Text(context.state.isInClass ? "距离下课" : "即将上课")
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.trailing)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
        .padding(.trailing, 4)
    }

    /// Full-width area beneath the hardware camera cutout styled like Super Island.
    private func expandedBottomView(
        context: ActivityViewContext<NexioCourseActivityAttributes>
    ) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(context.attributes.courseName)
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(.white)
                .lineLimit(1)

            HStack(spacing: 6) {
                if !context.state.section.isEmpty {
                    Text(context.state.section)
                }
                if !context.state.section.isEmpty && !context.state.classroom.isEmpty {
                    Text("|")
                        .foregroundStyle(Color.secondary.opacity(0.6))
                }
                if !context.state.classroom.isEmpty {
                    Text(context.state.classroom)
                }
            }
            .font(.system(size: 13))
            .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 2)
        .padding(.top, 2)
    }

    // MARK: - Helpers

    private func timerWidth(
        context: ActivityViewContext<NexioCourseActivityAttributes>,
        compact: Bool
    ) -> CGFloat {
        let target = context.state.isInClass
            ? context.state.endEpochSeconds
            : context.attributes.startEpochSeconds
        let includesHours = target - Date().timeIntervalSince1970 >= 3600
        return compact ? (includesHours ? 62 : 42) : (includesHours ? 96 : 68)
    }

    private func countdownRange(
        context: ActivityViewContext<NexioCourseActivityAttributes>
    ) -> ClosedRange<Date> {
        let now = Date()
        let start = Date(timeIntervalSince1970: context.attributes.startEpochSeconds)
        let end = Date(timeIntervalSince1970: context.state.endEpochSeconds)
        if !context.state.isInClass && start > now {
            return now...start
        } else {
            return min(now, start)...end
        }
    }
}
