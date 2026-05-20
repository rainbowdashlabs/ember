frontend/src/views/dashboardview/OverviewView.vue Make the different panels independent components.

station/inventory/checks Allow sorting by last checked date.

After finishing a inventory check, choose the next user based on the role of the last user. If a user was a member, go to the next member. Only go to a next member if the member was not checked today.

Allow members to see the detailed view of events as well. Show them which users are registered or have set an explicit attendence status already. Also allow them to register themself. Make sure that event notifications for the event directly lead to the detailed page.

Make sure to keep the help center updated