from locust import Locust, TaskSet, task
import resource

# Set file limit a bit higher to enable higher concurrency
soft, hard = resource.getrlimit(resource.RLIMIT_NOFILE)
resource.setrlimit(resource.RLIMIT_NOFILE, (2048, hard))

# Test TaskSet
class MyTaskSet(TaskSet):

    # Home page
    @task(20)
    def index(self):
        response = self.client.get("/resource", headers={'Accept':'application/json'})

# Test user
class WebsiteUser(Locust):
    task_set = MyTaskSet
    min_wait=500
    max_wait=1000
