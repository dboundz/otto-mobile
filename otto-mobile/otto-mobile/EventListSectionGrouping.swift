import SwiftUI

enum EventListSection: Hashable {
    case today
    case thisWeek
    case nextWeek
    case thisMonth
    case calendarMonth(year: Int, month: Int)
}

struct EventListSectionGroup: Identifiable {
    let section: EventListSection
    let title: String
    let items: [EventDTO]

    var id: EventListSection { section }
}

enum EventListSectionedPresentation {
    case featured
    case compact
}

func sortEventsForSectionedList(_ events: [EventDTO]) -> [EventDTO] {
    events.sorted { $0.startsAt < $1.startsAt }
}

func groupEventsByListSection(
    _ events: [EventDTO],
    now: Date = Date(),
    calendar: Calendar = .current
) -> [EventListSectionGroup] {
    let sorted = sortEventsForSectionedList(events)
    var groupsBySection: [EventListSection: [EventDTO]] = [:]
    var sectionOrder: [EventListSection] = []

    for event in sorted {
        let section = eventListSection(for: event.startsAt, now: now, calendar: calendar)
        if groupsBySection[section] == nil {
            sectionOrder.append(section)
            groupsBySection[section] = []
        }
        groupsBySection[section]?.append(event)
    }

    return sectionOrder.compactMap { section in
        guard let items = groupsBySection[section], !items.isEmpty else { return nil }
        return EventListSectionGroup(
            section: section,
            title: eventListSectionTitle(for: section, now: now, calendar: calendar),
            items: items
        )
    }
}

func eventListSection(
    for date: Date,
    now: Date,
    calendar: Calendar
) -> EventListSection {
    if calendar.isDate(date, inSameDayAs: now) {
        return .today
    }

    if let thisWeek = calendar.dateInterval(of: .weekOfYear, for: now),
       thisWeek.contains(date) {
        return .thisWeek
    }

    if let thisWeek = calendar.dateInterval(of: .weekOfYear, for: now),
       let nextWeekStart = calendar.date(byAdding: .day, value: 7, to: thisWeek.start),
       let nextWeek = calendar.dateInterval(of: .weekOfYear, for: nextWeekStart),
       nextWeek.contains(date) {
        return .nextWeek
    }

    let dateYear = calendar.component(.year, from: date)
    let dateMonth = calendar.component(.month, from: date)
    let nowYear = calendar.component(.year, from: now)
    let nowMonth = calendar.component(.month, from: now)

    if dateYear == nowYear, dateMonth == nowMonth {
        return .thisMonth
    }

    return .calendarMonth(year: dateYear, month: dateMonth)
}

func eventListSectionTitle(
    for section: EventListSection,
    now: Date,
    calendar: Calendar
) -> String {
    switch section {
    case .today:
        return String(localized: "events_section_today").uppercased()
    case .thisWeek:
        return String(localized: "events_section_this_week").uppercased()
    case .nextWeek:
        return String(localized: "events_section_next_week").uppercased()
    case .thisMonth:
        return String(localized: "events_section_this_month").uppercased()
    case let .calendarMonth(year, month):
        return eventListCalendarMonthTitle(year: year, month: month, now: now, calendar: calendar)
    }
}

private func eventListCalendarMonthTitle(
    year: Int,
    month: Int,
    now: Date,
    calendar: Calendar
) -> String {
    var components = DateComponents()
    components.year = year
    components.month = month
    components.day = 1
    guard let date = calendar.date(from: components) else { return "" }

    let nowYear = calendar.component(.year, from: now)
    let formatter = DateFormatter()
    formatter.locale = calendar.locale ?? Locale.current
    formatter.timeZone = calendar.timeZone
    formatter.dateFormat = year == nowYear ? "MMMM" : "MMMM yyyy"
    return formatter.string(from: date).uppercased()
}

struct EventListSectionHeader: View {
    let title: String
    var isFirst: Bool = false

    var body: some View {
        HStack(spacing: 8) {
            Text(title)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Color.white.opacity(0.42))
                .fixedSize(horizontal: true, vertical: false)
            Rectangle()
                .fill(Color.white.opacity(0.12))
                .frame(height: 1)
        }
        .padding(.top, isFirst ? 0 : 14)
        .padding(.bottom, 6)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.black)
    }
}

struct EventListSectionedList<Header: View, Footer: View, Row: View>: View {
    let events: [EventDTO]
    let presentation: EventListSectionedPresentation
    let horizontalPadding: CGFloat
    let hasListHeader: Bool
    let headerBottomPadding: CGFloat
    let compactFirstSectionAfterHeader: Bool
    let showFooter: Bool
    @ViewBuilder var header: () -> Header
    @ViewBuilder var footer: () -> Footer
    let row: (EventDTO, Bool) -> Row

    init(
        events: [EventDTO],
        presentation: EventListSectionedPresentation,
        horizontalPadding: CGFloat = OttoScreenChrome.horizontalPadding,
        hasListHeader: Bool = false,
        headerBottomPadding: CGFloat = 8,
        compactFirstSectionAfterHeader: Bool = false,
        showFooter: Bool = false,
        @ViewBuilder header: @escaping () -> Header = { EmptyView() },
        @ViewBuilder footer: @escaping () -> Footer = { EmptyView() },
        @ViewBuilder row: @escaping (EventDTO, Bool) -> Row
    ) {
        self.events = events
        self.presentation = presentation
        self.horizontalPadding = horizontalPadding
        self.hasListHeader = hasListHeader
        self.headerBottomPadding = headerBottomPadding
        self.compactFirstSectionAfterHeader = compactFirstSectionAfterHeader
        self.showFooter = showFooter
        self.header = header
        self.footer = footer
        self.row = row
    }

    private var groups: [EventListSectionGroup] {
        groupEventsByListSection(events)
    }

    var body: some View {
        List {
            if hasListHeader {
                Section {
                    header()
                        .padding(.horizontal, horizontalPadding)
                        .padding(.bottom, headerBottomPadding)
                }
                .listRowInsets(EdgeInsets())
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
            }

            ForEach(Array(groups.enumerated()), id: \.element.id) { index, group in
                Section {
                    sectionContent(for: group)
                } header: {
                    EventListSectionHeader(
                        title: group.title,
                        isFirst: index == 0 && (!hasListHeader || compactFirstSectionAfterHeader)
                    )
                        .padding(.leading, horizontalPadding)
                }
            }

            if showFooter {
                Section {
                    footer()
                        .padding(.horizontal, horizontalPadding)
                        .padding(.top, 8)
                }
                .listRowInsets(EdgeInsets())
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
            }
        }
        .listStyle(.plain)
        .listSectionSpacing(6)
        .contentMargins(.top, 0, for: .scrollContent)
        .scrollContentBackground(.hidden)
        .background(Color.black)
    }

    @ViewBuilder
    private func sectionContent(for group: EventListSectionGroup) -> some View {
        switch presentation {
        case .featured:
            ForEach(group.items) { event in
                row(event, false)
                    .listRowInsets(
                        EdgeInsets(
                            top: 7,
                            leading: horizontalPadding,
                            bottom: 7,
                            trailing: horizontalPadding
                        )
                    )
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
            }
        case .compact:
            EventListGroupedSectionContainer {
                ForEach(Array(group.items.enumerated()), id: \.element.id) { index, event in
                    row(event, true)
                    if index < group.items.count - 1 {
                        Divider().overlay(Color.white.opacity(0.08))
                    }
                }
            }
            .listRowInsets(
                EdgeInsets(
                    top: 0,
                    leading: horizontalPadding,
                    bottom: 7,
                    trailing: horizontalPadding
                )
            )
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
        }
    }
}

private struct EventListGroupedSectionContainer<Content: View>: View {
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(spacing: 0) {
            content()
        }
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.white.opacity(0.055))
        )
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.10), lineWidth: 1)
                .allowsHitTesting(false)
        }
    }
}
